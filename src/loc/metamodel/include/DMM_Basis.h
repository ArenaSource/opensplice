/*
 *                         OpenSplice DDS
 *
 *   This software and documentation are Copyright 2006 to 2013 PrismTech
 *   Limited and its licensees. All rights reserved. See file:
 *
 *                     $OSPL_HOME/LICENSE 
 *
 *   for full copyright notice and license terms. 
 *
 */
#ifndef DLRL_META_MODEL_BASIS_H
#define DLRL_META_MODEL_BASIS_H

#if defined (__cplusplus)
extern "C" {
#endif
#include "os_if.h"

#ifdef OSPL_BUILD_LOC_METAMODEL
#define OS_API OS_API_EXPORT
#else
#define OS_API OS_API_IMPORT
#endif
/* !!!!!!!!NOTE From here no more includes are allowed!!!!!!! */

/* contains recommended enumeration sentinel */
typedef enum DMM_Basis {
    DMM_BASIS_STR_MAP,
    DMM_BASIS_INT_MAP,
    DMM_BASIS_SET,
    /* NOT IN DESIGN DMM_BASIS_REF, */
    DMM_Basis_elements
} DMM_Basis;

#undef OS_API

#if defined (__cplusplus)
}
#endif

#endif /* DLRL_META_MODEL_BASIS_H */
