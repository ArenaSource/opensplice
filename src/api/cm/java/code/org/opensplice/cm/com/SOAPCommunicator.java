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
package org.opensplice.cm.com;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.Timer;

import org.opensplice.cm.CMException;
import org.opensplice.cm.DataReader;
import org.opensplice.cm.DataTypeUnsupportedException;
import org.opensplice.cm.Entity;
import org.opensplice.cm.EntityFilter;
import org.opensplice.cm.Participant;
import org.opensplice.cm.Partition;
import org.opensplice.cm.Publisher;
import org.opensplice.cm.Query;
import org.opensplice.cm.Reader;
import org.opensplice.cm.ReaderSnapshot;
import org.opensplice.cm.Service;
import org.opensplice.cm.ServiceState;
import org.opensplice.cm.Snapshot;
import org.opensplice.cm.Storage.Result;
import org.opensplice.cm.Subscriber;
import org.opensplice.cm.Time;
import org.opensplice.cm.Topic;
import org.opensplice.cm.Waitset;
import org.opensplice.cm.Writer;
import org.opensplice.cm.WriterSnapshot;
import org.opensplice.cm.data.GID;
import org.opensplice.cm.data.Sample;
import org.opensplice.cm.data.UserData;
import org.opensplice.cm.meta.MetaType;
import org.opensplice.cm.qos.ParticipantQoS;
import org.opensplice.cm.qos.PublisherQoS;
import org.opensplice.cm.qos.QoS;
import org.opensplice.cm.qos.ReaderQoS;
import org.opensplice.cm.qos.SubscriberQoS;
import org.opensplice.cm.qos.TopicQoS;
import org.opensplice.cm.qos.WriterQoS;
import org.opensplice.cm.statistics.Statistics;
import org.opensplice.cm.status.Status;
import org.opensplice.cm.transform.DataTransformerFactory;
import org.opensplice.cm.transform.EntityDeserializer;
import org.opensplice.cm.transform.EntitySerializer;
import org.opensplice.cm.transform.MetaTypeDeserializer;
import org.opensplice.cm.transform.QoSDeserializer;
import org.opensplice.cm.transform.QoSSerializer;
import org.opensplice.cm.transform.SampleDeserializer;
import org.opensplice.cm.transform.SnapshotDeserializer;
import org.opensplice.cm.transform.SnapshotSerializer;
import org.opensplice.cm.transform.StatisticsDeserializer;
import org.opensplice.cm.transform.StatusDeserializer;
import org.opensplice.cm.transform.StorageDeserializer;
import org.opensplice.cm.transform.StorageSerializer;
import org.opensplice.cm.transform.TransformationException;
import org.opensplice.cm.transform.UserDataSerializer;

/**
 * SOAP implementation of the Control & Monitoring communication interface
 * (Communicator). When this communicator is plugged into the Control &
 * Monitoring API, communication with SPLICE-DDS domains on remote nodes is
 * possible if the Control & Monitoring SOAP Service is running on that node/
 * domain combination.
 * 
 * @date Jan 17, 2005
 */
public class SOAPCommunicator implements Communicator, ActionListener {
    private org.opensplice.cm.com.SOAPConnection connection = null;
    private SOAPConnection connection2 = null;
    private String url = null;
    private EntityDeserializer entityDeserializer;
    private MetaTypeDeserializer typeDeserializer;
    private EntitySerializer entitySerializer;
    private StatusDeserializer statusDeserializer;
    private SnapshotDeserializer snapshotDeserializer;
    private SnapshotSerializer snapshotSerializer;
    private SampleDeserializer untypedSampleDeserializer;
    private UserDataSerializer userDataSerializer;
    private QoSDeserializer qosDeserializer;
    private StorageDeserializer storageDeserializer;
    private StorageSerializer storageSerializer;
    private Timer updateLease;
    private SOAPMessage leaseRequest;
    private QoSSerializer qosSerializer;
    private StatisticsDeserializer statisticsDeserializer;
    private boolean initialized;
    private boolean connectionAlive;

    /**
     * Creates a new SOAP communication handler for the Control & Monitoring
     * API.
     * 
     * @throws Exception
     *             Thrown when: - The Java SOAP extensions are not available.
     */
    public SOAPCommunicator() throws CommunicationException {
        initialized = false;
        connectionAlive = false;

        try {
            entityDeserializer = DataTransformerFactory.getEntityDeserializer(
                                                DataTransformerFactory.XML);
            typeDeserializer = DataTransformerFactory.getMetaTypeDeserializer(
                                                DataTransformerFactory.XML);
            entitySerializer = DataTransformerFactory.getEntitySerializer(
                                                DataTransformerFactory.XML);
            statusDeserializer = DataTransformerFactory.getStatusDeserializer(
                                                DataTransformerFactory.XML);
            snapshotDeserializer = DataTransformerFactory.getSnapshotDeserializer(
                                                DataTransformerFactory.XML);
            snapshotSerializer = DataTransformerFactory.getSnapshotSerializer(
                                                DataTransformerFactory.XML);
            untypedSampleDeserializer = DataTransformerFactory.getUntypedSampleDeserializer(
                                                DataTransformerFactory.XML);
            userDataSerializer = DataTransformerFactory.getUserDataSerializer(
                                                DataTransformerFactory.XML);
            qosDeserializer = DataTransformerFactory.getQoSDeserializer(
                                                DataTransformerFactory.XML);
            qosSerializer = DataTransformerFactory.getQoSSerializer(
                                                DataTransformerFactory.XML);
            statisticsDeserializer = DataTransformerFactory.getStatisticsDeserializer(
                                                DataTransformerFactory.XML);
            storageDeserializer = DataTransformerFactory.getStorageDeserializer(
                                                DataTransformerFactory.XML);
            storageSerializer = DataTransformerFactory.getStorageSerializer(
                                                DataTransformerFactory.XML);

            updateLease = new Timer(5*1000, this);
            updateLease.setRepeats(false);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("dummy", "dummy");
            leaseRequest = this.createRequest("updateLease", members);
        } catch (Exception e) {
            throw new CommunicationException(e.getMessage());
        }
    }

    @Override
    public void initialise(String _url) throws CommunicationException {
        try {
            connection = new SOAPConnection();
            connection2 = new SOAPConnection();
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("dummy", "dummy");
            SOAPMessage request = this.createRequest("initialise", members);
            SOAPMessage response = connection.call(request, _url);
            url = _url;
            String result = this.getResponse(response);

            if((result == null) || (!(result.equals("<result>OK</result>")))){
                throw new CommunicationException("Could not initialise.");
            }
            initialized = true;
            connectionAlive = true;
            updateLease.start();
        } catch (UnsupportedOperationException e) {
            throw new CommunicationException(e.getMessage());
        } catch (SOAPException e) {
            this.connectionAlive = false;
            this.checkConnection();
        }
    }

    @Override
    public void detach() throws CommunicationException {
        if(connection == null){
            throw new CommunicationException("No current connection");
        }
        try {
            if(this.initialized && this.connectionAlive){
                initialized = false;
                connectionAlive = false;
                SortedMap<String, String> members = new TreeMap<String, String>();
                members.put("dummy", "dummy");
                SOAPMessage request = this.createRequest("detach", members);
                SOAPMessage response = connection.call(request, url);
                String result = this.getResponse(response);

                if((result == null) || (!(result.equals("<result>OK</result>")))){
                    throw new CommunicationException("Could not detach.");
                }
                updateLease.stop();
                connection.close();
                connection2.close();
                connection = null;
                connection2 = null;
            }
        } catch (UnsupportedOperationException e) {
            throw new CommunicationException(e.getMessage());
        } catch (SOAPException e) {
            this.connectionAlive = false;
            this.checkConnection();
        }
    }

    @Override
    public void entityFree(Entity entity) throws CommunicationException {
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(entity);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);
            SOAPMessage request = this.createRequest("entityFree", members);
            SOAPMessage result = connection.call(request, url);
            this.getEmptyResponse(result);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException(te.getMessage());
        }
    }

    @Override
    public Entity[] entityOwnedEntities(Entity entity, EntityFilter filter) throws CommunicationException {
        Entity[] e = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(entity);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);
            members.put("filter", EntityFilter.getString(filter));
            SOAPMessage request = this.createRequest("entityOwnedEntities", members);
            SOAPMessage result = connection2.call(request, url);
            String entities = this.getResponse(result);
            e = entityDeserializer.deserializeEntityList(entities);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve owned entities.");
        }
        return e;

    }

    @Override
    public Entity[] entityDependantEntities(Entity entity, EntityFilter filter) throws CommunicationException {
        Entity[] e = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(entity);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);
            members.put("filter", EntityFilter.getString(filter));
            SOAPMessage request = this.createRequest("entityDependantEntities", members);
            SOAPMessage result = connection2.call(request, url);
            String entities = this.getResponse(result);
            e = entityDeserializer.deserializeEntityList(entities);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve dependant entities.");
        }
        return e;

    }

    @Override
    public Participant participantNew(String uri, int timeout, String name, ParticipantQoS qos) throws CommunicationException {
        Participant participant = null;
        String xmlQos = null;
        this.checkConnection();

        try{
            if(qos != null){
                xmlQos = qosSerializer.serializeQoS(qos);
            }
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("uri", uri);
            members.put("timeout", Integer.toString(timeout));
            members.put("name", name);
            members.put("qos", xmlQos);
            SOAPMessage request = this.createRequest("participantNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            participant = (Participant)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create participant");
        }
        return participant;
    }

    @Override
    public Participant[] participantAllParticipants(Participant p) throws CommunicationException {
        Participant[] result = null;
        this.checkConnection();

        try{
            String entities = null;
            String xmlEntity = entitySerializer.serializeEntity(p);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlEntity);
            SOAPMessage request = this.createRequest("participantAllParticipants", members);
            SOAPMessage soapResult = connection2.call(request, url);
            entities = this.getResponse(soapResult);
            Entity[] entityResult = entityDeserializer.deserializeEntityList(entities);
            result = new Participant[entityResult.length];

            for(int i=0; i<entityResult.length; i++){
                result[i] = (Participant)(entityResult[i]);
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve all partiticipants.");
        }
        return result;
    }

    @Override
    public Topic[] participantAllTopics(Participant p) throws CommunicationException {
        Topic[] result = null;
        this.checkConnection();

        try{
            String entities = null;
            String xmlEntity = entitySerializer.serializeEntity(p);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlEntity);
            SOAPMessage request = this.createRequest("participantAllTopics", members);
            SOAPMessage soapResult = connection2.call(request, url);
            entities = this.getResponse(soapResult);
            Entity[] entityResult = entityDeserializer.deserializeEntityList(entities);
            result = new Topic[entityResult.length];

            for(int i=0; i<entityResult.length; i++){
                result[i] = (Topic)(entityResult[i]);
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve all topics");
        }
        return result;
    }

    @Override
    public Partition[] participantAllDomains(Participant p) throws CommunicationException {
        Partition[] result = null;
        this.checkConnection();

        try{
            String entities = null;
            String xmlEntity = entitySerializer.serializeEntity(p);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlEntity);
            SOAPMessage request = this.createRequest("participantAllDomains", members);
            SOAPMessage soapResult = connection2.call(request, url);

            entities = this.getResponse(soapResult);
            Entity[] entityResult = entityDeserializer.deserializeEntityList(entities);
            result = new Partition[entityResult.length];

            for(int i=0; i<entityResult.length; i++){
                result[i] = (Partition)(entityResult[i]);
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve all partitions");
        }
        return result;
    }

    @Override
    public Topic[] participantFindTopic(Participant participant, String topicName) throws CommunicationException {
        Topic[] result = null;
        this.checkConnection();

        try{
            String entities = null;
            String xmlEntity = entitySerializer.serializeEntity(participant);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlEntity);
            members.put("topicName", topicName);
            SOAPMessage request = this.createRequest("participantFindTopic", members);
            SOAPMessage soapResult = connection2.call(request, url);

            entities = this.getResponse(soapResult);
            Entity[] entityResult = entityDeserializer.deserializeEntityList(entities);
            result = new Topic[entityResult.length];

            for(int i=0; i<entityResult.length; i++){
                result[i] = (Topic)(entityResult[i]);
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not find topic.");
        }
        return result;
    }

    @Override
    public MetaType topicGetDataType(Topic topic) throws CommunicationException, DataTypeUnsupportedException {
        MetaType result = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(topic);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("topic", xmlEntity);
            SOAPMessage request = this.createRequest("topicDataType", members);
            updateLease.restart();
            SOAPMessage response = connection.call(request, url);
            String xmlType = this.getResponse(response);
            result = typeDeserializer.deserializeMetaType(xmlType);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve topic data type.");
        }
        return result;

    }

    @Override
    public MetaType readerGetDataType(Reader reader) throws CommunicationException, DataTypeUnsupportedException {
        MetaType result = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(reader);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("reader", xmlEntity);
            SOAPMessage request = this.createRequest("readerDataType", members);
            updateLease.restart();
            SOAPMessage response = connection.call(request, url);
            String xmlType = this.getResponse(response);
            result = typeDeserializer.deserializeMetaType(xmlType);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve topic data type of reader.");
        }
        return result;
    }

    @Override
    public MetaType writerGetDataType(Writer writer) throws CommunicationException, DataTypeUnsupportedException {
        MetaType result = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(writer);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("writer", xmlEntity);
            SOAPMessage request = this.createRequest("writerDataType", members);
            SOAPMessage response = connection.call(request, url);
            String xmlType = this.getResponse(response);
            result = typeDeserializer.deserializeMetaType(xmlType);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve topic data type of writer.");
        }
        return result;
    }

    @Override
    public ServiceState serviceGetState(Service service) throws CommunicationException {
        ServiceState state = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(service);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("service", xmlEntity);
            SOAPMessage request = this.createRequest("serviceGetState", members);
            SOAPMessage response = connection.call(request, url);
            String xmlState = this.getResponse(response);
            state = (ServiceState)(entityDeserializer.deserializeEntity(xmlState));
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve service state.");
        }
        return state;

    }

    @Override
    public String getVersion() throws CommunicationException {
        String version = "N.A.";
        this.checkConnection();

        try {
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("dummy", "dummy");
            SOAPMessage request = this.createRequest("getVersion", members);
            SOAPMessage response = connection.call(request, url);
            version = this.getResponse(response);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (NoSuchMethodError e) {
            version = "N.A.";
        }

        if (version == null) {
            version = "N.A.";
        }

        return version;
    }

    @Override
    public Sample readerRead(Reader reader) throws CommunicationException, DataTypeUnsupportedException {
        Sample result = null;
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(reader);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("reader", xmlEntity);
            SOAPMessage request = this.createRequest("readerRead", members);
            SOAPMessage response = connection.call(request, url);
            String xmlSample = this.getResponse(response);
            result = untypedSampleDeserializer.deserializeSample(xmlSample, reader.getDataType());
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not read sample.");
        } catch (CMException ce) {
            throw new CommunicationException(ce.getMessage());
        }
        return result;
    }

    @Override
    public Sample readerTake(Reader reader) throws CommunicationException, DataTypeUnsupportedException {
        Sample result = null;
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(reader);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("reader", xmlEntity);
            SOAPMessage request = this.createRequest("readerTake", members);
            SOAPMessage response = connection.call(request, url);
            String xmlSample = this.getResponse(response);
            result = untypedSampleDeserializer.deserializeSample(xmlSample, reader.getDataType());
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not take sample.");
        } catch (CMException ce) {
            throw new CommunicationException(ce.getMessage());
        }
        return result;
    }

    @Override
    public Sample readerReadNext(Reader reader, GID instanceGID) throws CommunicationException, DataTypeUnsupportedException {
        Sample result = null;
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(reader);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("reader", xmlEntity);
            members.put("localId", Long.toString(instanceGID.getLocalId()));
            members.put("systemId", Long.toString(instanceGID.getSystemId()));

            SOAPMessage request = this.createRequest("readerReadNext", members);
            SOAPMessage response = connection.call(request, url);
            String xmlSample = this.getResponse(response);
            result = untypedSampleDeserializer.deserializeSample(xmlSample, reader.getDataType());
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not readNext sample.");
        } catch (CMException ce) {
            throw new CommunicationException(ce.getMessage());
        }
        return result;
    }

    @Override
    public Status entityGetStatus(Entity entity) throws CommunicationException {
        Status status = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(entity);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);
            SOAPMessage request = this.createRequest("entityGetStatus", members);
            SOAPMessage response = connection.call(request, url);
            String xmlStatus = this.getResponse(response);
            status = statusDeserializer.deserializeStatus(xmlStatus);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve entity status.");
        }
        return status;
    }

    @Override
    public ReaderSnapshot readerSnapshotNew(Reader reader) throws CommunicationException {
        ReaderSnapshot rs = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(reader);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("reader", xmlEntity);
            SOAPMessage request = this.createRequest("readerSnapshotNew", members);
            SOAPMessage response = connection.call(request, url);
            String xmlSnapshot = this.getResponse(response);
            rs = snapshotDeserializer.deserializeReaderSnapshot(xmlSnapshot, reader);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create a snapshot of the supplied reader.");
        }
        return rs;
    }

    @Override
    public WriterSnapshot writerSnapshotNew(Writer writer) throws CommunicationException {
        WriterSnapshot ws = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(writer);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("writer", xmlEntity);
            SOAPMessage request = this.createRequest("writerSnapshotNew", members);
            SOAPMessage response = connection.call(request, url);
            String xmlSnapshot = this.getResponse(response);
            ws = snapshotDeserializer.deserializeWriterSnapshot(xmlSnapshot, writer);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create a snapshot of the supplied writer.");
        }
        return ws;

    }

    @Override
    public void snapshotFree(Snapshot snapshot) throws CommunicationException {
        this.checkConnection();

        try{
            String xmlSnapshot = snapshotSerializer.serializeSnapshot(snapshot);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("snapshot", xmlSnapshot);
            SOAPMessage request = this.createRequest("snapshotFree", members);
            SOAPMessage response = connection.call(request, url);
            this.getEmptyResponse(response);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not free snapshot.");
        }
    }

    @Override
    public Sample snapshotRead(Snapshot snapshot) throws CommunicationException, DataTypeUnsupportedException {
        Sample s = null;
        this.checkConnection();

        try{
            String xmlSnapshot = snapshotSerializer.serializeSnapshot(snapshot);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("snapshot", xmlSnapshot);
            SOAPMessage request = this.createRequest("snapshotRead", members);
            SOAPMessage response = connection.call(request, url);
            String xmlSample = this.getResponse(response);
            s = untypedSampleDeserializer.deserializeSample(xmlSample, snapshot.getUserDataType());
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not read from snapshot.");
        } catch (CMException ce) {
            throw new CommunicationException(ce.getMessage());
        }
        return s;
    }

    @Override
    public Sample snapshotTake(Snapshot snapshot) throws CommunicationException, DataTypeUnsupportedException {
        Sample s = null;
        this.checkConnection();

        try{
            String xmlSnapshot = snapshotSerializer.serializeSnapshot(snapshot);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("snapshot", xmlSnapshot);
            SOAPMessage request = this.createRequest("snapshotTake", members);
            SOAPMessage response = connection.call(request, url);
            String xmlSample = this.getResponse(response);
            s = untypedSampleDeserializer.deserializeSample(xmlSample, snapshot.getUserDataType());
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not take from snapshot.");
        } catch (CMException ce) {
            throw new CommunicationException(ce.getMessage());
        }
        return s;
    }

    @Override
    public void writerWrite(Writer writer, UserData data) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(writer);
            String xmlData = userDataSerializer.serializeUserData(data);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("writer", xmlEntity);
            members.put("userData", xmlData);
            SOAPMessage request = this.createRequest("writerWrite", members);
            SOAPMessage response = connection.call(request, url);
            String xmlResult = this.getResponse(response);

            if(!("<result>OK</result>".equals(xmlResult))){
                throw new CommunicationException("Could not write data.");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not write data with suppplied writer.");
        }
    }

    @Override
    public void writerDispose(Writer writer, UserData data) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(writer);
            String xmlData = userDataSerializer.serializeUserData(data);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("writer", xmlEntity);
            members.put("userData", xmlData);
            SOAPMessage request = this.createRequest("writerDispose", members);
            SOAPMessage response = connection.call(request, url);
            String xmlResult = this.getResponse(response);

            if(!("<result>OK</result>".equals(xmlResult))){
                throw new CommunicationException("Could not dispose data.");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not dispose data with suppplied writer.");
        }
    }

    @Override
    public void writerWriteDispose(Writer writer, UserData data) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(writer);
            String xmlData = userDataSerializer.serializeUserData(data);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("writer", xmlEntity);
            members.put("userData", xmlData);
            SOAPMessage request = this.createRequest("writerWriteDispose", members);
            SOAPMessage response = connection.call(request, url);
            String xmlResult = this.getResponse(response);

            if(!("<result>OK</result>".equals(xmlResult))){
                throw new CommunicationException("Could not writeDspose data.");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not writeDispose data with suppplied writer.");
        }
    }

    @Override
    public void writerRegister(Writer writer, UserData data) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(writer);
            String xmlData = userDataSerializer.serializeUserData(data);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("writer", xmlEntity);
            members.put("userData", xmlData);
            SOAPMessage request = this.createRequest("writerRegister", members);
            SOAPMessage response = connection.call(request, url);
            String xmlResult = this.getResponse(response);

            if(!("<result>OK</result>".equals(xmlResult))){
                throw new CommunicationException("Could not register instance.");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not register instance with suppplied writer.");
        }
    }

    @Override
    public void writerUnregister(Writer writer, UserData data) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(writer);
            String xmlData = userDataSerializer.serializeUserData(data);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("writer", xmlEntity);
            members.put("userData", xmlData);
            SOAPMessage request = this.createRequest("writerUnregister", members);
            SOAPMessage response = connection.call(request, url);
            String xmlResult = this.getResponse(response);

            if(!("<result>OK</result>".equals(xmlResult))){
                throw new CommunicationException("Could not unregister instance.");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not unregister instance with suppplied writer.");
        }
    }

    @Override
    public Publisher publisherNew(Participant p, String name, PublisherQoS qos) throws CommunicationException {
        Publisher entity = null;
        String xmlQos = null;
        this.checkConnection();

        try{
            if(qos != null){
                xmlQos = qosSerializer.serializeQoS(qos);
            }
            String xmlParticipant = entitySerializer.serializeEntity(p);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlParticipant);
            members.put("name", name);
            members.put("qos", xmlQos);
            SOAPMessage request = this.createRequest("publisherNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            entity = (Publisher)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create publisher.");
        }
        return entity;
    }

    @Override
    public Subscriber subscriberNew(Participant p, String name, SubscriberQoS qos) throws CommunicationException {
        Subscriber entity = null;
        String xmlQos = null;
        this.checkConnection();

        try{
            if(qos != null){
                xmlQos = qosSerializer.serializeQoS(qos);
            }
            String xmlParticipant = entitySerializer.serializeEntity(p);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlParticipant);
            members.put("name", name);
            members.put("qos", xmlQos);
            SOAPMessage request = this.createRequest("subscriberNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            entity = (Subscriber)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create subscriber.");
        }
        return entity;
    }

    @Override
    public Partition partitionNew(Participant p, String name) throws CommunicationException {
        Partition entity = null;
        this.checkConnection();

        try{
            String xmlParticipant = entitySerializer.serializeEntity(p);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlParticipant);
            members.put("name", name);
            SOAPMessage request = this.createRequest("domainNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            entity = (Partition)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create partition.");
        }
        return entity;
    }

    @Override
    public void publisherPublish(Publisher p, String expression) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlPublisher = entitySerializer.serializeEntity(p);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("publisher", xmlPublisher);
            members.put("expression", expression);

            SOAPMessage request = this.createRequest("publisherPublish", members);
            SOAPMessage response = connection.call(request, url);
            String xmlResult = this.getResponse(response);

            if(!("<result>OK</result>".equals(xmlResult))){
                throw new CommunicationException("Publication failed.");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Publication failed.");
        }

    }

    @Override
    public void subscriberSubscribe(Subscriber p, String expression) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlSubscriber = entitySerializer.serializeEntity(p);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("subscriber", xmlSubscriber);
            members.put("expression", expression);

            SOAPMessage request = this.createRequest("subscriberSubscribe", members);
            SOAPMessage response = connection.call(request, url);
            String xmlResult = this.getResponse(response);

            if(!("<result>OK</result>".equals(xmlResult))){
                throw new CommunicationException("Subscription failed.");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Subscription failed.");
        }
    }

    @Override
    public Writer writerNew(Publisher p, String name, Topic t, WriterQoS qos) throws CommunicationException {
        Writer entity = null;
        String xmlQos = null;
        this.checkConnection();

        try{
            if(qos != null){
                xmlQos = qosSerializer.serializeQoS(qos);
            }
            String xmlPublisher = entitySerializer.serializeEntity(p);
            String xmlTopic = entitySerializer.serializeEntity(t);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("publisher", xmlPublisher);
            members.put("name", name);
            members.put("topic", xmlTopic);
            members.put("qos", xmlQos);

            SOAPMessage request = this.createRequest("writerNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            entity = (Writer)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create writer.");
        }
        return entity;
    }

    @Override
    public DataReader dataReaderNew(Subscriber s, String name, String viewExpression, ReaderQoS qos) throws CommunicationException {
        DataReader entity = null;
        String xmlQos = null;
        this.checkConnection();

        try{
            if(qos != null){
                xmlQos = qosSerializer.serializeQoS(qos);
            }
            String xmlSubscriber = entitySerializer.serializeEntity(s);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("subscriber", xmlSubscriber);
            members.put("name", name);
            members.put("view", viewExpression);
            members.put("qos", xmlQos);

            SOAPMessage request = this.createRequest("dataReaderNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            entity = (DataReader)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create datareader.");
        }
        return entity;
    }

    @Override
    public void dataReaderWaitForHistoricalData(DataReader dr, Time time) throws CommunicationException{
        this.checkConnection();

        try{
            String xmlDataReader = entitySerializer.serializeEntity(dr);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("dataReader", xmlDataReader);
            members.put("seconds", Integer.toString(time.sec));
            members.put("nanoseconds", Integer.toString(time.nsec));

            SOAPMessage request = this.createRequest("dataReaderWaitForHistoricalData", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException("Wait for historical data failed: " + result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("dataReaderWaitForHistoricalData failed.");
        }
    }

    @Override
    public Query queryNew(Reader source, String name, String expression) throws CommunicationException {
        Query entity = null;
        this.checkConnection();

        try{
            String xmlReader = entitySerializer.serializeEntity(source);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("reader", xmlReader);
            members.put("name", name);
            members.put("expression", expression);

            SOAPMessage request = this.createRequest("queryNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            entity = (Query)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create query.");
        }
        return entity;
    }

    @Override
    public Topic topicNew(Participant p, String name, String typeName, String keyList, TopicQoS qos) throws CommunicationException {
        Topic entity = null;
        String xmlQos = null;
        this.checkConnection();

        try {
            if(qos != null){
                xmlQos = qosSerializer.serializeQoS(qos);
            }
            String xmlParticipant = entitySerializer.serializeEntity(p);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlParticipant);
            members.put("name", name);
            members.put("typeName", typeName);
            members.put("keyList", keyList);
            members.put("qos", xmlQos);

            SOAPMessage request = this.createRequest("topicNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlEntity = this.getResponse(result);
            entity = (Topic)entityDeserializer.deserializeEntity(xmlEntity);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create topic.");
        }
        return entity;
    }

    @Override
    public Waitset waitsetNew(Participant participant) throws CommunicationException {
        Waitset waitset = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(participant);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlEntity);

            SOAPMessage request = this.createRequest("waitsetNew", members);
            SOAPMessage result = connection.call(request, url);
            String xmlWaitset= this.getResponse(result);
            waitset = (Waitset)entityDeserializer.deserializeEntity(xmlWaitset);

        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not create waitset.");
        }
        return waitset;
    }

    @Override
    public QoS entityGetQoS(Entity entity) throws CommunicationException {
        QoS qos = null;
        this.checkConnection();

        try{
            String xmlEntity = entitySerializer.serializeEntity(entity);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);
            SOAPMessage request = this.createRequest("entityGetQos", members);
            SOAPMessage response = connection.call(request, url);
            String xmlQos = this.getResponse(response);
            qos = qosDeserializer.deserializeQoS(xmlQos);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Could not resolve qos.");
        }
        return qos;
    }

    @Override
    public void entitySetQoS(Entity entity, QoS qos) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(entity);
            String xmlQoS = qosSerializer.serializeQoS(qos);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);
            members.put("qos", xmlQoS);
            SOAPMessage request = this.createRequest("entitySetQos", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException(result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch(TransformationException te){
            throw new CommunicationException("Applying new qos failed.");
        }
    }

    @Override
    public void participantRegisterType(Participant participant, MetaType type) throws CommunicationException{
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(participant);
            String xmlType = type.toXML();

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("participant", xmlEntity);
            members.put("type", xmlType);

            SOAPMessage request = this.createRequest("participantRegisterType", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException(result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not register type");
        }
    }

    @Override
    public Statistics entityGetStatistics(Entity entity) throws CommunicationException {
        Statistics statistics = null;
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(entity);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);

            SOAPMessage request = this.createRequest("entityStatistics", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if((result != null) &&(!("".equals(result)))){
                statistics = statisticsDeserializer.deserializeStatistics(result, entity);
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not reset statistics.");
        }
        return statistics;
    }

    @Override
    public void entityResetStatistics(Entity entity, String fieldName) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(entity);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);
            members.put("fieldName", fieldName);

            SOAPMessage request = this.createRequest("entityResetStatistics", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException(result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not reset statistics.");
        }
    }

    @Override
    public void entityEnable(Entity entity) throws CommunicationException {
        this.checkConnection();

        try {
            String xmlEntity = entitySerializer.serializeEntity(entity);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("entity", xmlEntity);

            SOAPMessage request = this.createRequest("entityEnable", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException(result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not enable entity.");
        }
    }

    @Override
    public void waitsetAttach(Waitset waitset, Entity entity) throws CommunicationException {
        this.checkConnection();

        try{
            String xmlWaitset = entitySerializer.serializeEntity(waitset);
            String xmlEntity  = entitySerializer.serializeEntity(entity);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("waitset", xmlWaitset);
            members.put("entity", xmlEntity);

            SOAPMessage request = this.createRequest("waitsetAttach", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException(result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not attach entity to waitset.");
        }
        return;
    }

    @Override
    public void waitsetDetach(Waitset waitset, Entity entity) throws CommunicationException {
        this.checkConnection();

        try{
            String xmlWaitset = entitySerializer.serializeEntity(waitset);
            String xmlEntity  = entitySerializer.serializeEntity(entity);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("waitset", xmlWaitset);
            members.put("entity", xmlEntity);

            SOAPMessage request = this.createRequest("waitsetDetach", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException(result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not attach entity to waitset.");
        }
        return;
    }

    @Override
    public Entity[] waitsetWait(Waitset waitset) throws CommunicationException{
        Entity[] entities = null;
        this.checkConnection();

        try{
            String xmlWaitset = entitySerializer.serializeEntity(waitset);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("waitset", xmlWaitset);

            SOAPMessage request = this.createRequest("waitsetWait", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            Entity[] entityResult = entityDeserializer.deserializeEntityList(result);

            if(entityResult != null){
                entities = new Entity[entityResult.length];

                for(int i=0; i<entityResult.length; i++){
                    entities[i] = entityResult[i];
                }
            } else {
                throw new CommunicationException("waitsetWait failed (2)");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("waitsetWait failed.");
        }
        return entities;
    }

    @Override
    public Entity[] waitsetTimedWait(Waitset waitset, Time time) throws CommunicationException{
        Entity[] entities = null;
        this.checkConnection();

        try{
            String xmlWaitset = entitySerializer.serializeEntity(waitset);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("waitset", xmlWaitset);
            members.put("seconds", Integer.toString(time.sec));
            members.put("nanoseconds", Integer.toString(time.nsec));

            SOAPMessage request = this.createRequest("waitsetTimedWait", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            Entity[] entityResult = entityDeserializer.deserializeEntityList(result);

            if(entityResult != null){
                entities = new Entity[entityResult.length];

                for(int i=0; i<entityResult.length; i++){
                    entities[i] = entityResult[i];
                }
            } else {
                throw new CommunicationException("waitsetTimedWait failed (2)");
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("waitsetTimedWait failed.");
        }
        return entities;
    }

    @Override
    public int waitsetGetEventMask(Waitset waitset) throws CommunicationException {
        int mask = 0;
        this.checkConnection();

        try{
            String xmlWaitset = entitySerializer.serializeEntity(waitset);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("waitset", xmlWaitset);

            SOAPMessage request = this.createRequest("waitsetGetEventMask", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);
            mask = Integer.parseInt(result);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not resolve event mask of waitset.");
        }
        return mask;
    }

    @Override
    public void waitsetSetEventMask(Waitset waitset, int mask) throws CommunicationException {
        this.checkConnection();

        try{
            String xmlWaitset = entitySerializer.serializeEntity(waitset);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("waitset", xmlWaitset);
            members.put("mask", Integer.toString(mask));

            SOAPMessage request = this.createRequest("waitsetSetEventMask", members);
            SOAPMessage response = connection.call(request, url);
            String result = this.getResponse(response);

            if(!("<result>OK</result>".equals(result))){
                throw new CommunicationException(result.substring(8, result.length() -9));
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not set event mask of waitset.");
        }
        return;
    }

    @Override
    public Object storageOpen(String attrs) throws CommunicationException {
        Object storage = null;
        String result;

        this.checkConnection();
        try{
            /* attrs is already XML, so doesn't need to be serialized */
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("attrs", attrs);

            SOAPMessage request = this.createRequest("storageOpen", members);
            SOAPMessage response = connection.call(request, url);
            result = this.getResponse(response);

            Result r = storageDeserializer.deserializeOpenResult_Result(result);

            if(!r.equals(Result.SUCCESS)){
                /* TODO: create StorageException or something like that */
            } else {
                storage = storageDeserializer.deserializeOpenResult_Storage(result);
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not deserialize storageOpen result.");
        }

        return storage;
    }

    @Override
    public Result storageClose(Object storage) throws CommunicationException{
        Result r = Result.ERROR;
        String result;

        this.checkConnection();
        try{
            String xmlStorage = storageSerializer.serializeStorage(storage);
            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("storage", xmlStorage);

            SOAPMessage request = this.createRequest("storageClose", members);
            SOAPMessage response = connection.call(request, url);
            result = this.getResponse(response);

            r = storageDeserializer.deserializeStorageResult(result);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not deserialize storageClose result.");
        }

        return r;
    }

    @Override
    public Result storageAppend(Object storage, UserData data) throws CommunicationException {
        Result r = Result.ERROR;
        String result;

        this.checkConnection();
        try{
            String xmlStorage = storageSerializer.serializeStorage(storage);
            String xmlType = data.getUserDataType().toXML();
            String xmlData = userDataSerializer.serializeUserData(data);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("storage", xmlStorage);
            members.put("metadata", xmlType);
            members.put("data", xmlData);

            SOAPMessage request = this.createRequest("storageAppend", members);
            SOAPMessage response = connection.call(request, url);
            result = this.getResponse(response);

            r = storageDeserializer.deserializeStorageResult(result);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not deserialize storageAppend result.");
        }

        return r;
    }

    @Override
    public UserData storageRead(Object storage) throws CommunicationException {
        UserData data = null;
        String result;

        this.checkConnection();
        try{
            String xmlStorage = storageSerializer.serializeStorage(storage);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("storage", xmlStorage);

            SOAPMessage request = this.createRequest("storageRead", members);
            SOAPMessage response = connection.call(request, url);
            result = this.getResponse(response);

            Result r = storageDeserializer.deserializeReadResult_Result(result);
            if(!r.equals(Result.SUCCESS)){
                /* TODO: create StorageException or something like that */
            } else {
                /* Retrieve the metadata. TODO: Only resolve if type unkown; i.e.
                 * cache the output. */
                String typeName = storageDeserializer.deserializeReadResult_DataTypeName(result);
                if (typeName != null) {
                    MetaType type = storageGetType(storage, typeName);
                    data = storageDeserializer.deserializeReadResult_Data(result, type);
                } /* Else read returned NULL */
            }
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not deserialize storageRead result.");
        }

        return data;
    }

    @Override
    public MetaType storageGetType(Object storage, String typeName) throws CommunicationException {
        MetaType meta = null;
        String result;

        this.checkConnection();
        try{
            String xmlStorage = storageSerializer.serializeStorage(storage);
            String xmlTypeName = storageSerializer.serializeTypeName(typeName);

            SortedMap<String, String> members = new TreeMap<String, String>();
            members.put("storage", xmlStorage);
            members.put("typeName", xmlTypeName);

            SOAPMessage request = this.createRequest("storageGetType", members);
            SOAPMessage response = connection.call(request, url);
            result = this.getResponse(response);

            meta = storageDeserializer.deserializeGetTypeResult_Metadata(result);
        } catch (SOAPException se) {
            this.connectionAlive = false;
            this.checkConnection();
        } catch (TransformationException e) {
            throw new CommunicationException("Could not deserialize storageGetType result.");
        } catch (DataTypeUnsupportedException e){
            throw new CommunicationException("Datatype returned by storage not supported.");
        }
        return meta;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource().equals(updateLease)){
            try {
                this.checkConnection();

                SOAPMessage response = connection.call(leaseRequest, url);
                String result = this.getResponse(response);

                if((result != null) && ((result.equals("<result>OK</result>")))){
                    updateLease.restart();
                }
            }
            catch (SOAPException se) {
                this.connectionAlive = false;
            }
            catch (CommunicationException ce) {
                this.connectionAlive = false;
            }
        }
    }

    private SOAPMessage createRequest(String method, SortedMap<String, String> members){
        SOAPRequest message;
        String value, key;

        message = new SOAPRequest();
        message.setMethod(method);

        if(members != null){
            for (Iterator<Entry<String, String>> it = members.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, String> entry = it.next();
                key = entry.getKey();
                value = entry.getValue();
                message.addBodyParameter(key, value);
            }
        }
        message.saveChanges();

        return message;
    }

    private String getResponse(SOAPMessage response) throws CommunicationException {
        String result = null;

        if(response == null){
            throw new CommunicationException("Empty response received.");
        }
        updateLease.restart();
        result = ((SOAPResponse)response).getBodyContent();

        return result;
    }

    private void getEmptyResponse(SOAPMessage response) throws CommunicationException {
        if(response == null){
            throw new CommunicationException("null input.");
        }
        updateLease.restart();
    }

    private void checkConnection() throws CommunicationException{
        if(this.initialized && !this.connectionAlive){
            throw new ConnectionLostException();
        }
    }
}

