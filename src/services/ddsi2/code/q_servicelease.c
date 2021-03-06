#include <assert.h>

#include "os_if.h"
#include "os_defs.h"
#include "os_heap.h"
#include "os_mutex.h"
#include "os_cond.h"
#include "os_thread.h"

#include "u_user.h"
#include "u_participant.h"
#include "u_service.h"

#include "q_servicelease.h"
#include "q_config.h"
#include "q_log.h"
#include "q_thread.h"
#include "q_time.h"
#include "q_error.h"

#include "sysdeps.h" /* for getrusage() */

static void nn_retrieve_lease_settings (v_duration *leaseExpiryTime, os_time *sleepTime)
{
  const c_float leaseSec = config.servicelease_expiry_time;
  c_float sleepSec = leaseSec * config.servicelease_update_factor;

  /* Run at no less than 1Hz: internal liveliness monitoring is slaved
     to this interval as well.  1Hz lease renewals and liveliness
     checks is no large burden, and performing liveliness checks once
     a second is a lot more useful than doing it once every few
     seconds.  Besides -- we're now also gathering CPU statistics. */
  if (sleepSec > 1.0f)
    sleepSec = 1.0f;

  sleepTime->tv_sec = (os_int32) sleepSec;
  sleepTime->tv_nsec = (os_int32) ((sleepSec - (float) sleepTime->tv_sec) * 1e9f);
  leaseExpiryTime->seconds = (os_int32) leaseSec;
  leaseExpiryTime->nanoseconds = (os_int32) ((leaseSec - (float) leaseExpiryTime->seconds) * 1e9f);
}

struct alive_wd {
  char alive;
  vtime_t wd;
};

struct nn_servicelease {
  v_duration leasePeriod;
  os_time sleepTime;
  int keepgoing;
  struct alive_wd *av_ary;
  u_participant participant;

  os_mutex lock;
  os_cond cond;
  struct thread_state1 *ts;
};

static void *lease_renewal_thread (struct nn_servicelease *sl)
{
  /* Do not check more often than once every 100ms (no particular
     reason why it has to be 100ms), regardless of the lease settings.
     Note: can't trust sl->self, may have been scheduled before the
     assignment. */
  const os_int64 min_progress_check_intv = 100 * T_MILLISECOND;
  struct thread_state1 *self = lookup_thread_state ();
  os_int64 tlast = 0;
  int i;
  for (i = 0; i < thread_states.nthreads; i++)
  {
    sl->av_ary[i].alive = 1;
    sl->av_ary[i].wd = thread_states.ts[i].watchdog - 1;
  }
  os_mutexLock (&sl->lock);
  while (sl->keepgoing)
  {
    int n_alive = 0;
    os_int64 tnow = now ();

    TRACE (("servicelease: tnow %lld:", tnow));

    /* Check progress only if enough time has passed: there is no
       guarantee that os_condTimedWait wont ever return early, and we
       do want to avoid spurious warnings. */
    if (tnow < tlast + min_progress_check_intv)
    {
      n_alive = thread_states.nthreads;
    }
    else
    {
      tlast = tnow;
      for (i = 0; i < thread_states.nthreads; i++)
      {
        if (thread_states.ts[i].state != THREAD_STATE_ALIVE)
          n_alive++;
        else
        {
          vtime_t vt = thread_states.ts[i].vtime;
          vtime_t wd = thread_states.ts[i].watchdog;
          int alive = vtime_asleep_p (vt) || vtime_asleep_p (wd) || vtime_gt (wd, sl->av_ary[i].wd);
          n_alive += alive;
          TRACE ((" %d(%s):%c:%u:%u->%u:", i, thread_states.ts[i].name, alive ? 'a' : 'd', vt, sl->av_ary[i].wd, wd));
          sl->av_ary[i].wd = wd;
          if (sl->av_ary[i].alive != alive)
          {
            const char *name = thread_states.ts[i].name;
            const char *msg;
            if (!alive)
              msg = "failed to make progress";
            else
              msg = "once again made progress";
            NN_WARNING2 ("thread %s %s\n", name ? name : "(anon)", msg);
            sl->av_ary[i].alive = alive;
          }
        }
      }
    }

    /* Only renew the lease if all threads are alive, so that one
       thread blocking for a while but not too extremely long will
       cause warnings for that thread in the log file, but won't cause
       the DDSI2 service to be marked as dead. */
    if (n_alive == thread_states.nthreads)
    {
      TRACE ((": [%d] renewing\n", n_alive));
      u_serviceRenewLease (u_service (sl->participant), sl->leasePeriod);
    }
    else
    {
      TRACE ((": [%d] NOT renewing\n", n_alive));
    }

#if SYSDEPS_HAVE_GETRUSAGE
    /* If getrusage() is available, use it to log CPU and memory
       statistics to the trace.  Getrusage() can't fail if the
       parameters are valid, and these are by the book.  Still we
       check. */
    if (config.enabled_logcats & LC_TIMING)
    {
      struct rusage u;
      if (getrusage (RUSAGE_SELF, &u) == 0)
      {
        nn_log (LC_TIMING,
                "rusage: utime %d.%06d stime %d.%06d maxrss %ld data %ld vcsw %ld ivcsw %ld\n",
                (int) u.ru_utime.tv_sec, (int) u.ru_utime.tv_usec,
                (int) u.ru_stime.tv_sec, (int) u.ru_stime.tv_usec,
                u.ru_maxrss, u.ru_idrss, u.ru_nvcsw, u.ru_nivcsw);
      }
    }
#endif

    if (os_condTimedWait (&sl->cond, &sl->lock, &sl->sleepTime) == os_resultFail)
      NN_FATAL0 ("lease_renewal_thread: os_condTimedWait failed\n");

    /* We are never active in a way that matters for the garbage
       collection of old writers, &c. */
    thread_state_asleep (self);
  }
  os_mutexUnlock (&sl->lock);
  return NULL;
}

struct nn_servicelease *nn_servicelease_new (u_participant participant)
{
  struct nn_servicelease *sl;
  os_mutexAttr mattr;
  os_condAttr cattr;

  if ((sl = os_malloc (sizeof (*sl))) == NULL)
    goto fail_0;
  nn_retrieve_lease_settings (&sl->leasePeriod, &sl->sleepTime);
  sl->keepgoing = -1;
  sl->participant = participant;
  sl->ts = NULL;

  if ((sl->av_ary = os_malloc (thread_states.nthreads * sizeof (*sl->av_ary))) == NULL)
    goto fail_vtimes;
  /* service lease update thread initializes av_ary */

  os_mutexAttrInit (&mattr);
  mattr.scopeAttr = OS_SCOPE_PRIVATE;
  if (os_mutexInit (&sl->lock, &mattr) != os_resultSuccess)
    goto fail_lock;

  os_condAttrInit (&cattr);
  cattr.scopeAttr = OS_SCOPE_PRIVATE;
  if (os_condInit (&sl->cond, &sl->lock, &cattr) != os_resultSuccess)
    goto fail_cond;
  return sl;

 fail_cond:
  os_mutexDestroy (&sl->lock);
 fail_lock:
  os_free (sl->av_ary);
 fail_vtimes:
  os_free (sl);
 fail_0:
  return NULL;
}

int nn_servicelease_start_renewing (struct nn_servicelease *sl)
{
  os_mutexLock (&sl->lock);
  assert (sl->keepgoing == -1);
  sl->keepgoing = 1;
  os_mutexUnlock (&sl->lock);

  sl->ts = create_thread ("lease", (void * (*) (void *)) lease_renewal_thread, sl);
  if (sl->ts == NULL)
    goto fail_thread;
  return 0;

 fail_thread:
  sl->keepgoing = -1;
  return ERR_UNSPECIFIED;
}

void nn_servicelease_statechange_barrier (struct nn_servicelease *sl)
{
  os_mutexLock (&sl->lock);
  os_mutexUnlock (&sl->lock);
}

void nn_servicelease_free (struct nn_servicelease *sl)
{
  if (sl->keepgoing != -1)
  {
    os_mutexLock (&sl->lock);
    sl->keepgoing = 0;
    os_condSignal (&sl->cond);
    os_mutexUnlock (&sl->lock);
    join_thread (sl->ts, (void **) 0);
  }
  os_condDestroy (&sl->cond);
  os_mutexDestroy (&sl->lock);
  os_free (sl->av_ary);
  os_free (sl);
}

/* SHA1 not available (unoffical build.) */
