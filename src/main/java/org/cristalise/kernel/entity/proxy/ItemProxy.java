/**
 * This file is part of the CRISTAL-iSE kernel.
 * Copyright (c) 2001-2015 The CRISTAL Consortium. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; with out even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 * http://www.fsf.org/licensing/licenses/lgpl.html
 */
package org.cristalise.kernel.entity.proxy;

import static org.cristalise.kernel.persistency.ClusterType.HISTORY;
import static org.cristalise.kernel.property.BuiltInItemProperties.NAME;
import static org.cristalise.kernel.property.BuiltInItemProperties.TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.cristalise.kernel.collection.BuiltInCollections;
import org.cristalise.kernel.collection.Collection;
import org.cristalise.kernel.collection.CollectionArrayList;
import org.cristalise.kernel.common.AccessRightsException;
import org.cristalise.kernel.common.InvalidCollectionModification;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.InvalidTransitionException;
import org.cristalise.kernel.common.ObjectAlreadyExistsException;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.common.PersistencyException;
import org.cristalise.kernel.entity.C2KLocalObject;
import org.cristalise.kernel.entity.Item;
import org.cristalise.kernel.entity.ItemHelper;
import org.cristalise.kernel.entity.agent.Job;
import org.cristalise.kernel.entity.agent.JobArrayList;
import org.cristalise.kernel.events.Event;
import org.cristalise.kernel.lifecycle.instance.CompositeActivity;
import org.cristalise.kernel.lifecycle.instance.Workflow;
import org.cristalise.kernel.lifecycle.instance.predefined.WriteProperty;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.persistency.ClusterType;
import org.cristalise.kernel.persistency.outcome.Outcome;
import org.cristalise.kernel.persistency.outcome.OutcomeAttachment;
import org.cristalise.kernel.persistency.outcome.Schema;
import org.cristalise.kernel.persistency.outcome.Viewpoint;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.property.BuiltInItemProperties;
import org.cristalise.kernel.property.Property;
import org.cristalise.kernel.property.PropertyArrayList;
import org.cristalise.kernel.querying.Query;
import org.cristalise.kernel.utils.CastorXMLUtility;
import org.cristalise.kernel.utils.LocalObjectLoader;
import org.cristalise.kernel.utils.Logger;
import org.exolab.castor.mapping.MappingException;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;

import lombok.Getter;
import lombok.Setter;


/**
 * It is a wrapper for the connection and communication with Item.
 * It caches data loaded from the Item to reduce communication
 */
public class ItemProxy
{
    protected Item                  mItem = null;
    protected ItemPath              mItemPath;
    protected org.omg.CORBA.Object  mIOR;

    private final HashMap<MemberSubscription<?>, ProxyObserver<?>> mSubscriptions;

    /**
     * Set Transaction key (aka locker) when ItemProxy is used in server side scripting
     */
    @Getter @Setter
    private Object transactionKey = null;

    /**
     *
     * @param ior
     * @param itemPath
     */
    protected ItemProxy( org.omg.CORBA.Object  ior, ItemPath itemPath) {
        Logger.msg(8, "ItemProxy::initialise() - path:" +itemPath);

        mIOR            = ior;
        mItemPath       = itemPath;
        mSubscriptions  = new HashMap<MemberSubscription<?>, ProxyObserver<?>>();
    }

    /**
     * Return the ItemPath object of the Item this proxy is linked with
     * @return the ItemPath of the Item this proxy is linked with
     */
    public ItemPath getPath() {
        return mItemPath;
    }

    /**
     * Returns the CORBA Item this proxy is linked with
     *
     * @return the CORBA Item this proxy is linked with
     * @throws ObjectNotFoundException there was a problem connecting with the Item
     */
    protected Item getItem() throws ObjectNotFoundException {
        if (mItem == null) mItem = narrow();
        return mItem;
    }

    /**
     * Narrows the CORBA Item this proxy is linked with
     *
     * @return the CORBA Item this proxy is linked with
     * @throws ObjectNotFoundException there was a problem connecting with the Item
     */
    public Item narrow() throws ObjectNotFoundException {
        try {
            return ItemHelper.narrow(mIOR);
        }
        catch (org.omg.CORBA.BAD_PARAM ex) {
            throw new ObjectNotFoundException("CORBA Object was not an Item, or the server is down:" + ex.getMessage());
        }
    }

    /**
     * Initialise the new Item with instance data which was normally created from descriptions
     *
     * @param agentId the Agent who is creating the Item
     * @param itemProps initial list of Properties of the Item
     * @param workflow new Lifecycle of the Item
     * @param colls the initial state of the Item's collections
     *
     * @throws AccessRightsException Agent does not have the rights to create an Item
     * @throws InvalidDataException data was invalid
     * @throws PersistencyException there was a database problem during Item initialisation
     * @throws ObjectNotFoundException Object not found
     * @throws MarshalException there was a problem converting those objects to XML
     * @throws ValidationException XML was not valid
     * @throws IOException IO errors
     * @throws MappingException errors in XML marshall/unmarshall mapping
     * @throws InvalidCollectionModification invalid Collection
     */
    public void initialise(AgentPath agentId, 
                           PropertyArrayList itemProps, 
                           CompositeActivity workflow, 
                           CollectionArrayList colls
                           )
             throws AccessRightsException,
                    InvalidDataException,
                    PersistencyException,
                    ObjectNotFoundException,
                    MarshalException,
                    ValidationException,
                    IOException,
                    MappingException,
                    InvalidCollectionModification
    {
        initialise(agentId, itemProps, workflow, colls, null, null);
    }

    /**
     * Initialise the new Item with instance data which was normally created from descriptions
     *
     * @param agentId the Agent who is creating the Item
     * @param itemProps initial list of Properties of the Item
     * @param workflow new Lifecycle of the Item
     * @param colls the initial state of the Item's collections
     * @param viewpoint the provide viewpoint to be stored for the Outcome
     * @param outcome the Outcome to be used (like the parameters of the class constructor)
     *
     * @throws AccessRightsException Agent does not have the rights to create an Item
     * @throws InvalidDataException data was invalid
     * @throws PersistencyException there was a database problem during Item initialisation
     * @throws ObjectNotFoundException Object not found
     * @throws MarshalException there was a problem converting those objects to XML
     * @throws ValidationException XML was not valid
     * @throws IOException IO errors
     * @throws MappingException errors in XML marshall/unmarshall mapping
     * @throws InvalidCollectionModification invalid Collection
     */
    public void initialise(AgentPath agentId, 
                           PropertyArrayList itemProps, 
                           CompositeActivity workflow, 
                           CollectionArrayList colls,
                           Viewpoint viewpoint,
                           Outcome outcome
                           )
            throws AccessRightsException,
                    InvalidDataException,
                    PersistencyException,
                    ObjectNotFoundException,
                    MarshalException,
                    ValidationException,
                    IOException,
                    MappingException,
                    InvalidCollectionModification
    {
        Logger.msg(7, "ItemProxy.initialise() - started");

        CastorXMLUtility xml = Gateway.getMarshaller();
        if (itemProps == null) throw new InvalidDataException("ItemProxy.initialise() - No initial properties supplied");
        String propString = xml.marshall(itemProps);

        String wfString = "";
        if (workflow != null) wfString = xml.marshall(workflow);

        String collString = "";
        if (colls != null) collString = xml.marshall(colls);

        String viewpointString = "";
        if (viewpoint != null) viewpointString = xml.marshall(viewpoint);

        String outcomeString = "";
        if (outcome != null) outcomeString = outcome.getData();

        getItem().initialise(agentId.getSystemKey(), propString, wfString, collString, viewpointString, outcomeString);
    }

    /**
     * Sets the value of the given Property
     *
     * @param agent the Agent who is setting the Property
     * @param name the name of the Property
     * @param value the value of the Property
     * @throws AccessRightsException Agent does not the rights to execute this operation
     * @throws PersistencyException there was a database problems during this operations
     * @throws InvalidDataException data was invalid
     */
    public void setProperty(AgentProxy agent, String name, String value)
            throws AccessRightsException, PersistencyException, InvalidDataException
    {
        try {
            String[] params = {name, value};
            agent.execute(this, WriteProperty.class.getSimpleName(), params);
        }
        catch (AccessRightsException | PersistencyException | InvalidDataException e) {
            throw (e);
        }
        catch (Exception e) {
            Logger.error(e);
            throw new PersistencyException("Could not store property:"+e.getMessage());
        }
    }

    /**
     * Executes the given Job
     *
     * @param thisJob the Job to be executed
     * @return the result of the execution
     * @throws AccessRightsException Agent does not the rights to execute this operation
     * @throws PersistencyException there was a database problems during this operations
     * @throws InvalidDataException data was invalid
     * @throws InvalidTransitionException the Transition cannot be executed
     * @throws ObjectNotFoundException Object not found
     * @throws ObjectAlreadyExistsException Object already exists
     * @throws InvalidCollectionModification Invalid collection
     */
    public String requestAction( Job thisJob )
            throws AccessRightsException,
                   InvalidTransitionException,
                   ObjectNotFoundException,
                   InvalidDataException,
                   PersistencyException,
                   ObjectAlreadyExistsException,
                   InvalidCollectionModification
    {
        if (thisJob.getAgentPath() == null) throw new InvalidDataException("No Agent specified.");

        String outcome = thisJob.getOutcomeString();

        if (outcome == null) {
            if (thisJob.isOutcomeRequired()) throw new InvalidDataException("Outcome is required.");
            else                             outcome = "";
        }
        
        OutcomeAttachment attachment = thisJob.getAttachment();
        String attachmentType = "";
        byte[] attachmentBinary = new byte[0];

        if (attachment != null) {
            attachmentType = attachment.getType();
            attachmentBinary = attachment.getBinaryData();
        }

        Logger.msg(7, "ItemProxy.requestAction() - executing "+thisJob.getStepPath()+" for "+thisJob.getAgentName());

        if (thisJob.getDelegatePath() == null) {
            return getItem().requestAction (
                    thisJob.getAgentPath().getSystemKey(), 
                    thisJob.getStepPath(),
                    thisJob.getTransition().getId(), 
                    outcome,
                    attachmentType,
                    attachmentBinary);
        }
        else {
            return getItem().delegatedAction(
                    thisJob.getAgentPath().getSystemKey(), 
                    thisJob.getDelegatePath().getSystemKey(),
                    thisJob.getStepPath(), 
                    thisJob.getTransition().getId(), 
                    outcome, 
                    attachmentType,
                    attachmentBinary);
        }
    }

    /**
     * Get the list of active Jobs of the Item that can be executed by the Agent
     *
     * @param agentPath the Agent requesting the job
     * @return list of active Jobs
     * @throws AccessRightsException Agent does not the rights to execute this operation
     * @throws PersistencyException there was a database problems during this operations
     * @throws ObjectNotFoundException data was invalid
     */
    public ArrayList<Job> getJobList(AgentPath agentPath) throws AccessRightsException, ObjectNotFoundException, PersistencyException {
        return getJobList(agentPath, false);
    }

    /**
     * Get the list of Jobs of the Item that can be executed by the Agent
     *
     * @param agentPath the Agent requesting the job
     * @param filter if true, then only Activities which are currently active will be included.
     * @return list of Jobs
     * @throws AccessRightsException Agent does not the rights to execute this operation
     * @throws PersistencyException there was a database problems during this operations
     * @throws ObjectNotFoundException data was invalid
     */
    private ArrayList<Job> getJobList(AgentPath agentPath, boolean filter)
            throws AccessRightsException, ObjectNotFoundException, PersistencyException
    {
        JobArrayList thisJobList;
        String jobs =  getItem().queryLifeCycle(agentPath.getSystemKey(), filter);

        try {
            thisJobList = (JobArrayList)Gateway.getMarshaller().unmarshall(jobs);
        }
        catch (Exception e) {
            Logger.error(e);
            throw new PersistencyException("Exception::ItemProxy::getJobList() - Cannot unmarshall the jobs");
        }
        return thisJobList.list;
    }

    /**
     * Get the list of active Jobs of the Item that can be executed by the Agent
     *
     * @param agent the Agent requesting the job
     * @return list of Jobs
     * @throws AccessRightsException Agent does not the rights to execute this operation
     * @throws PersistencyException there was a database problems during this operations
     * @throws ObjectNotFoundException data was invalid
     */
    public ArrayList<Job> getJobList(AgentProxy agent) throws AccessRightsException, ObjectNotFoundException, PersistencyException {
        return getJobList(agent.getPath(), true);
    }

    /**
     *
     * @param actName
     * @param agent
     * @return
     * @throws AccessRightsException
     * @throws ObjectNotFoundException
     * @throws PersistencyException
     */
    private Job getJobByName(String actName, AgentPath agent) throws AccessRightsException, ObjectNotFoundException, PersistencyException {
        ArrayList<Job> jobList = getJobList(agent, true);
        for (Job job : jobList) {
            if (job.getStepName().equals(actName) && job.getTransition().isFinishing())
                return job;
        }
        return null;
    }

    /**
     * Gets the current version of the named Collection
     *
     * @param collection The built-in collection
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(BuiltInCollections collection) throws ObjectNotFoundException {
        return getCollection(collection, (Integer)null);
    }

    /**
     * Gets the current version of the named Collection. This method can be used in server 
     * side Script to find uncommitted changes during the active transaction.
     *
     * @param collection The built-in collection
     * @param locker the transaction key
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(BuiltInCollections collection, Object locker) throws ObjectNotFoundException {
        return getCollection(collection, (Integer)null, locker == null ? transactionKey : locker);
    }

    /**
     * Gets a numbered version (snapshot) of a collection
     *
     * @param collection The built-in Collection
     * @param version The collection number. Use null to get the 'last' version.
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(BuiltInCollections collection, Integer version) throws ObjectNotFoundException {
        return getCollection(collection, version, transactionKey);
    }

    /**
     * Gets a numbered version (snapshot) of a collection
     *
     * @param collection The built-in Collection
     * @param version The collection number. Use null to get the 'last' version.
     * @param locker the transaction key
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(BuiltInCollections collection, Integer version, Object locker) throws ObjectNotFoundException {
        return getCollection(collection.getName(), version, locker == null ? transactionKey : locker);
    }

    /**
     * Gets the last version of the named collection
     *
     * @param collName The collection name
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(String collName) throws ObjectNotFoundException {
        return getCollection(collName, (Integer)null, transactionKey);
    }

    /**
     * Gets the last version of the named collection. This method can be used in server 
     * side Script to find uncommitted changes during the active transaction.
     *
     * @param collName The collection name
     * @param locker the transaction key
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(String collName, Object locker) throws ObjectNotFoundException {
        return getCollection(collName, (Integer)null, locker == null ? transactionKey : locker);
    }

    /**
     * Gets a numbered version (snapshot) of a collection
     *
     * @param collName The collection name
     * @param version The collection number. Use null to get the 'last' version.
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(String collName, Integer version) throws ObjectNotFoundException {
        return getCollection(collName, version, transactionKey);
    }

    /**
     * Gets a numbered version (snapshot) of a collection. This method can be used in server 
     * side Script to find uncommitted changes during the active transaction.
     *
     * @param collName The collection name
     * @param version The collection number. Use null to get the 'last' version.
     * @param locker the transaction key
     * @return the Collection object
     * @throws ObjectNotFoundException objects were not found
     */
    public Collection<?> getCollection(String collName, Integer version, Object locker) throws ObjectNotFoundException {
        String verStr = version == null ? "last" : String.valueOf(version);
        return (Collection<?>) getObject(ClusterType.COLLECTION+"/"+collName+"/"+verStr, locker == null ? transactionKey : locker);
    }

    /** 
     * Gets the Workflow object of this Item
     *
     * @return the Item's Workflow object
     * @throws ObjectNotFoundException objects were not found
     */
    public Workflow getWorkflow() throws ObjectNotFoundException {
        return getWorkflow(null);
    }

    /**
     * Gets the Workflow object of this Item. This method can be used in server 
     * side Script to find uncommitted changes during the active transaction.
     *
     * @param locker the transaction key
     * @return the Item's Workflow object
     * @throws ObjectNotFoundException objects were not found
     */
    public Workflow getWorkflow(Object locker) throws ObjectNotFoundException {
        return (Workflow)getObject(ClusterType.LIFECYCLE+"/workflow", locker == null ? transactionKey : locker);
    }

    /**
     * Check if the given Viewpoint exists
     *
     * @param schemaName the name of the Schema associated with the Viewpoint
     * @param viewName the name of the View
     * @return true if the ViewPoint exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkViewpoint(String schemaName, String viewName) throws ObjectNotFoundException {
        return checkContent(ClusterType.VIEWPOINT+"/"+schemaName, viewName);
    }

    /**
     * Check if the given Viewpoint exists.This method can be used in server 
     * side Script to find uncommitted changes during the active transaction.
     *
     * @param schemaName the name of the Schema associated with the Viewpoint
     * @param viewName the name of the View
     * @param locker the transaction key
     * @return true if the ViewPoint exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkViewpoint(String schemaName, String viewName, Object locker) throws ObjectNotFoundException {
        return checkContent(ClusterType.VIEWPOINT+"/"+schemaName, viewName, locker == null ? transactionKey : locker);
    }

    /**
     * Reads the list of existing Viewpoint names for the given schema 
     * 
     * @param schemaName the name of the schema
     * @return array of strings containing the Viewpoint names
     * @throws ObjectNotFoundException Object not found
     */
    public String[] getViewpoints(String schemaName) throws ObjectNotFoundException {
        return getContents(ClusterType.VIEWPOINT+"/"+schemaName);
    }

    /**
     * Reads the list of existing Viewpoint names for the given schema. This method can be used in server 
     * side Script to find uncommitted changes during the active transaction.
     * 
     * @param schemaName the name of the schema
     * @param locker the transaction key
     * @return array of strings containing the Viewpoint names
     * @throws ObjectNotFoundException Object not found
     */
    public String[] getViewpoints(String schemaName, Object locker) throws ObjectNotFoundException {
        return getContents(ClusterType.VIEWPOINT+"/"+schemaName, locker == null ? transactionKey : locker);
    }

    /**
     * Gets the named Viewpoint
     *
     * @param schemaName the name of the Schema associated with the Viewpoint
     * @param viewName name if the View
     * @return a Viewpoint object
     * @throws ObjectNotFoundException objects were not found
     */
    public Viewpoint getViewpoint(String schemaName, String viewName) throws ObjectNotFoundException {
        return (Viewpoint)getObject(ClusterType.VIEWPOINT+"/"+schemaName+"/"+viewName);
    }

    /**
     * Gets the named Viewpoint. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     * 
     * @param schemaName the name of the Schema associated with the Viewpoint
     * @param viewName name if the View
     * @param locker the transaction key
     * @return a Viewpoint object
     * @throws ObjectNotFoundException objects were not found
     */
    public Viewpoint getViewpoint(String schemaName, String viewName, Object locker) throws ObjectNotFoundException {
        return (Viewpoint)getObject(ClusterType.VIEWPOINT+"/"+schemaName+"/"+viewName, locker == null ? transactionKey : locker);
    }

    /**
     * Check if the given Outcome exists
     *
     * @param schemaName the name of the Schema used to create the Outcome
     * @param schemaVersion the version of the Schema used to create the Outcome
     * @param eventId the id of the Event created when the Outcome was stored
     * @return true if the Outcome exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkOutcome(String schemaName, int schemaVersion, int eventId) throws ObjectNotFoundException {
        return checkOutcome(schemaName, schemaVersion, eventId, transactionKey);
    }

    /**
     * Check if the given Outcome exists. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param schemaName the name of the Schema used to create the Outcome
     * @param schemaVersion the version of the Schema used to create the Outcome
     * @param eventId the id of the Event created when the Outcome was stored
     * @param locker the transaction key
     * @return true if the Outcome exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkOutcome(String schemaName, int schemaVersion, int eventId, Object locker) throws ObjectNotFoundException {
        try {
            return checkOutcome(LocalObjectLoader.getSchema(schemaName, schemaVersion), eventId, locker == null ? transactionKey : locker);
        }
        catch (InvalidDataException e) {
            Logger.error(e);
            throw new ObjectNotFoundException(e.getMessage());
        }
    }

    /**
     * Check if the given Outcome exists
     *
     * @param schema the Schema used to create the Outcome
     * @param eventId the id of the Event created when the Outcome was stored
     * @return true if the Outcome exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkOutcome(Schema schema, int eventId) throws ObjectNotFoundException {
        return checkOutcome(schema, eventId, transactionKey);
    }

    /**
     * Check if the given Outcome exists. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     * 
     * @param schema the Schema used to create the Outcome
     * @param eventId the id of the Event created when the Outcome was stored
     * @param locker transaction key
     * @return true if the Outcome exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkOutcome(Schema schema, int eventId, Object locker) throws ObjectNotFoundException {
        return checkContent(ClusterType.OUTCOME+"/"+schema.getName()+"/"+schema.getVersion(), String.valueOf(eventId), locker == null ? transactionKey : locker);
    }

    /**
     * Gets the selected Outcome. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param schemaName the name of the Schema of the Outcome
     * @param schemaVersion the version of the Schema of the Outcome
     * @param eventId the event id
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public Outcome getOutcome(String schemaName, int schemaVersion, int eventId) throws ObjectNotFoundException {
        return getOutcome(schemaName, schemaVersion, eventId, transactionKey);
    }

    /**
     * Gets the selected Outcome. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param schemaName the name of the Schema of the Outcome
     * @param schemaVersion the version of the Schema of the Outcome
     * @param eventId the event id
     * @param locker the transaction key
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public Outcome getOutcome(String schemaName, int schemaVersion, int eventId, Object locker) throws ObjectNotFoundException {
        try {
            return getOutcome(LocalObjectLoader.getSchema(schemaName, schemaVersion), eventId, locker == null ? transactionKey : locker);
        }
        catch (InvalidDataException e) {
            Logger.error(e);
            throw new ObjectNotFoundException(e.getMessage());
        }
    }

    /**
     * Gets the selected Outcome,
     *
     * @param schema the Schema used to create the Outcome
     * @param eventId the id of the Event created when the Outcome was stored
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public Outcome getOutcome(Schema schema, int eventId) throws ObjectNotFoundException {
        return getOutcome(schema, eventId, transactionKey);
    }

    /**
     * Gets the selected Outcome. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param schema the Schema used to create the Outcome
     * @param eventId the id of the Event created when the Outcome was stored
     * @param locker the transaction key
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public Outcome getOutcome(Schema schema, int eventId, Object locker) throws ObjectNotFoundException {
        return (Outcome)getObject(ClusterType.OUTCOME+"/"+schema.getName()+"/"+schema.getVersion()+"/"+eventId, locker == null ? transactionKey : locker);
    }

    /**
     * Gets the Outcome selected by the Viewpoint
     *
     * @param view the Viewpoint to be used
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public Outcome getOutcome(Viewpoint view) throws ObjectNotFoundException {
        return getOutcome(view, transactionKey);
    }

    /**
     * Gets the Outcome selected by the Viewpoint. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param view the Viewpoint to be used
     * @param locker the transaction key
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public Outcome getOutcome(Viewpoint view, Object locker) throws ObjectNotFoundException {
        try {
            return view.getOutcome(locker == null ? transactionKey : locker);
        }
        catch (PersistencyException e) {
            Logger.error(e);
            throw new ObjectNotFoundException(e.getMessage());
        }
    }

    /**
     * Gets the Outcome associated with the Event.
     * 
     * @param event the Event to be used
     * @return the Outcome object
     * @throws ObjectNotFoundException
     */
    public Outcome getOutcome(Event event) throws ObjectNotFoundException {
        return getOutcome(event, transactionKey);
    }

    /**
     * Gets the Outcome associated with the Event. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     * 
     * @param event the Event to be used
     * @param locker  the transaction key
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public Outcome getOutcome(Event event, Object locker) throws ObjectNotFoundException {
        return getOutcome(event.getSchemaName(), event.getSchemaVersion(), event.getID(), locker);
    }

    /**
     * Check if the given OutcomeAttachment exists
     *
     * @param schema the Schema used to create the Outcome and its OutcomeAttachment
     * @param eventId the id of the Event created when the Outcome and its OutcomeAttachment was stored
     * @return true if the OutcomeAttachment exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkOutcomeAttachment(Schema schema, int eventId) throws ObjectNotFoundException {
        return checkOutcomeAttachment(schema, eventId, transactionKey);
    }

    /**
     * Check if the given OutcomeAttachment exists. This method can be used in server side Script 
     * to find uncommitted changes during the active transaction.
     *
     * @param schema the Schema used to create the Outcome and its OutcomeAttachment
     * @param eventId the id of the Event created when the Outcome and its OutcomeAttachment was stored
     * @param locker the transaction key
     * @return true if the OutcomeAttachment exist false otherwise
     * @throws ObjectNotFoundException Object not found
     */
    public boolean checkOutcomeAttachment(Schema schema, int eventId, Object locker) throws ObjectNotFoundException {
        return checkContent(ClusterType.ATTACHMENT+"/"+schema.getName()+"/"+schema.getVersion(), String.valueOf(eventId), locker == null ? transactionKey : locker);
    }

    /**
     * Gets the selected OutcomeAttachment
     *
     * @param schemaName the name of the Schema used to create the Outcome and its OutcomeAttachment
     * @param schemaVersion the version of the Schema of the Outcome
     * @param eventId the event id
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public OutcomeAttachment getOutcomeAttachment(String schemaName, int schemaVersion, int eventId) throws ObjectNotFoundException {
        return getOutcomeAttachment(schemaName, schemaVersion, eventId, transactionKey);
    }

    /**
     * Gets the selected OutcomeAttachment. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param schemaName the name of the Schema used to create the Outcome and its OutcomeAttachment
     * @param schemaVersion the version of the Schema of the Outcome
     * @param eventId the event id
     * @param locker the transaction key
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public OutcomeAttachment getOutcomeAttachment(String schemaName, int schemaVersion, int eventId, Object locker)
            throws ObjectNotFoundException
    {
        try {
            return getOutcomeAttachment(LocalObjectLoader.getSchema(schemaName, schemaVersion), eventId, locker == null ? transactionKey : locker);
        }
        catch (InvalidDataException e) {
            Logger.error(e);
            throw new ObjectNotFoundException(e.getMessage());
        }
    }

    /**
     * Gets the selected OutcomeAttachment
     *
     * @param schema the Schema used to create the Outcome and its OutcomeAttachment
     * @param eventId the id of the Event created when the Outcome and the OutcomeAttachment was stored
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public OutcomeAttachment getOutcomeAttachment(Schema schema, int eventId) throws ObjectNotFoundException {
        return getOutcomeAttachment(schema, eventId, transactionKey);
    }

    /**
     * Gets the selected OutcomeAttachment. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param schema the Schema used to create the Outcome and its OutcomeAttachment
     * @param eventId the id of the Event created when the Outcome and the OutcomeAttachment was stored
     * @param locker the transaction key
     * @return the Outcome object
     * @throws ObjectNotFoundException object was not found
     */
    public OutcomeAttachment getOutcomeAttachment(Schema schema, int eventId, Object locker) throws ObjectNotFoundException {
        return (OutcomeAttachment)getObject(ClusterType.ATTACHMENT+"/"+schema.getName()+"/"+schema.getVersion()+"/"+eventId, locker == null ? transactionKey : locker);
    }

    /**
     * Finds the first finishing job with the given name for the given Agent in the workflow.
     *
     * @param actName the name of the Activity to look for
     * @param agent The agent to fetch jobs for
     * @return the JOB object or null if nothing was found
     * @throws AccessRightsException Agent has not rights
     * @throws ObjectNotFoundException objects were not found
     * @throws PersistencyException Error loading the relevant objects
     */
    public Job getJobByName(String actName, AgentProxy agent) throws AccessRightsException, ObjectNotFoundException,PersistencyException {
        return getJobByName(actName, agent.getPath());
    }

    /**
     * Finds the Job with the given Activity and Transition name for the Agent in the Items Workflow
     *
     * @param actName the name of the Activity to look for
     * @param transName the name of the Transition to look for
     * @param agent The AgentProxy to fetch jobs for
     * @return the JOB object or null if nothing was found
     * @throws AccessRightsException Agent has not rights
     * @throws ObjectNotFoundException objects were not found
     * @throws PersistencyException Error loading the relevant objects
     */
    public Job getJobByTransitionName(String actName, String transName, AgentProxy agent) throws AccessRightsException, ObjectNotFoundException,PersistencyException {
        return getJobByTransitionName(actName, transName, agent.getPath());
    }

    /**
     * Finds the Job with the given Activity and Transition name for the Agent in the Items Workflow
     *
     * @param actName the name of the Activity to look for
     * @param transName the name of the Transition to look for
     * @param agentPath The agent to fetch jobs for
     * @return the JOB object or null if nothing was found
     * @throws AccessRightsException Agent has not rights
     * @throws ObjectNotFoundException objects were not found
     * @throws PersistencyException Error loading the relevant objects
     */
    public Job getJobByTransitionName(String actName, String transName, AgentPath agentPath) throws AccessRightsException, ObjectNotFoundException,PersistencyException {
        for (Job job : getJobList(agentPath, true)) {
            if (job.getTransition().getName().equals(transName)) {
                if ((actName.contains("/") && job.getStepPath().equals(actName)) || job.getStepName().equals(actName))
                    return job;
            }
        }
        return null;
    }

    /**
     * If this is reaped, clear out the cache for it too.
     */
    @Override
    protected void finalize() throws Throwable {
        Logger.msg(7, "ItemProxy.finalize() - caches are reaped for item:"+mItemPath);
        Gateway.getStorage().clearCache(mItemPath, null);
        Gateway.getProxyManager().removeProxy(mItemPath);
        super.finalize();
    }

    /**
     * Query data of the Item located by the ClusterStorage path
     *
     * @param path the ClusterStorage path
     * @return the data in XML form
     * @throws ObjectNotFoundException path was not correct
     */
    public String queryData(String path) throws ObjectNotFoundException {
        return queryData(path, transactionKey);
    }

    /**
     * Query data of the Item located by the ClusterStorage path
     *
     * @param path the ClusterStorage path
     * @param locker the transaction key
     * @return the data in XML form
     * @throws ObjectNotFoundException path was not correct
     */
    public String queryData(String path, Object locker) throws ObjectNotFoundException {
        try {
            Logger.msg(7, "ItemProxy.queryData() - "+mItemPath+"/"+path);

            if (path.endsWith("all")) {
                Logger.msg(7, "ItemProxy.queryData() - listing contents");

                String[] result = Gateway.getStorage().getClusterContents(mItemPath, path.substring(0, path.length()-3));
                StringBuffer retString = new StringBuffer();

                for (int i = 0; i < result.length; i++) {
                    retString.append(result[i]);

                    if (i < result.length-1) retString.append(",");
                }
                Logger.msg(7, "ItemProxy.queryData() - "+retString.toString());
                return retString.toString();
            }
            else {
                C2KLocalObject target = Gateway.getStorage().get(mItemPath, path, locker == null ? transactionKey : locker);
                return Gateway.getMarshaller().marshall(target);
            }
        }
        catch (ObjectNotFoundException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.error(e);
            return "<ERROR>"+e.getMessage()+"</ERROR>";
        }
    }

    /**
     * Check if the data of the Item located by the ClusterStorage path is exist
     *
     * @param path the ClusterStorage path
     * @param name the name of the content to be checked
     * @return true if there is content false otherwise
     * @throws ObjectNotFoundException path was not correct
     */
    public boolean checkContent(String path, String name) throws ObjectNotFoundException {
        return checkContent(path, name, transactionKey);
    }

    /**
     * Check the root content of the given ClusterType
     *
     * @param cluster the type of the cluster
     * @param name the name of the content to be checked
     * @return true if there is content false otherwise
     * @throws ObjectNotFoundException path was not correct
     */
    public boolean checkContent(ClusterType cluster, String name) throws ObjectNotFoundException {
        return checkContent(cluster.getName(), name, transactionKey);
    }

    /**
     * Check if the data of the Item located by the ClusterStorage path is exist. This method can be used
     * in server side Script to find uncommitted changes during the active transaction.
     *
     * @param cluster the type of the cluster
     * @param name the name of the content to be checked
     * @param locker the transaction key
     * @return true if there is content false otherwise
     * @throws ObjectNotFoundException path was not correct
     */
    public boolean checkContent(String path, String name, Object locker) throws ObjectNotFoundException {
        for (String key : getContents(path, locker == null ? transactionKey : locker)) if (key.equals(name)) return true;
        return false;
    }

    /**
     * List the root content of the given ClusterType.
     *
     * @param type the type of the cluster
     * @return list of String of the cluster content
     * @throws ObjectNotFoundException Object nt found
     */
    public String[] getContents(ClusterType type) throws ObjectNotFoundException {
        return getContents(type.getName());
    }

    /**
     * List the root content of the given ClusterType. This method can be used in server side Script 
     * to find uncommitted changes during the active transaction.
     *
     * @param type the type of the cluster
     * @param locker the transaction key
     * @return list of String of the cluster content
     * @throws ObjectNotFoundException Object nt found
     */
    public String[] getContents(ClusterType type, Object locker) throws ObjectNotFoundException {
        return getContents(type.getName(), locker == null ? transactionKey : locker);
    }

    /**
     * List the content of the cluster located by the cluster path
     *
     * @param path the ClusterStorage path
     * @return list of String of the cluster content
     * @throws ObjectNotFoundException Object not found
     */
    public String[] getContents(String path) throws ObjectNotFoundException {
        return getContents(path, transactionKey);
    }

    /**
     * List the content of the cluster located by the cluster path. This method can be used in server side Script 
     * to find uncommitted changes during the active transaction.
     *
     * @param path the ClusterStorage path
     * @param locker the transaction key
     * @return list of String of the cluster content
     * @throws ObjectNotFoundException Object not found
     */
    public String[] getContents(String path, Object locker) throws ObjectNotFoundException {
        try {
            //return Gateway.getStorage().getClusterContents(mItemPath, path);
            return Gateway.getStorage().getClusterContents(mItemPath, path, locker == null ? transactionKey : locker);
        }
        catch (PersistencyException e) {
            throw new ObjectNotFoundException(e.toString());
        }
    }

    /**
     * Executes the Query in the target database. The query can be any of these type: SQL/OQL/XQuery/XPath/etc.
     *
     * @param query the query to be executed
     * @return the xml result of the query
     * @throws PersistencyException there was a fundamental DB issue
     */
    public String executeQuery(Query query) throws PersistencyException {
        return Gateway.getStorage().executeQuery(query);
    }

    /**
     * Retrieve the C2KLocalObject for the ClusterType
     *
     * @param type the ClusterTyoe
     * @return the C2KLocalObject
     * @throws ObjectNotFoundException the type did not result in a C2KLocalObject
     */
    public C2KLocalObject getObject(ClusterType type) throws ObjectNotFoundException {
        return getObject(type.getName());
    }

    /**
     * Retrieve the C2KLocalObject for the Cluster path
     *
     * @param path the path to the cluster content
     * @return the C2KLocalObject
     * @throws ObjectNotFoundException the path did not result in a C2KLocalObject
     */
    public C2KLocalObject getObject(String path) throws ObjectNotFoundException {
        return getObject(path, transactionKey);
    }

    /**
     * Retrieve the C2KLocalObject for the Cluster path. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     * 
     * @param path the path to the cluster object
     * @param locker the transaction key
     * @return the C2KLocalObject
     * @throws ObjectNotFoundException the path did not result in a C2KLocalObject
     */
    public C2KLocalObject getObject(String path, Object locker) throws ObjectNotFoundException {
        try {
            return Gateway.getStorage().get(mItemPath, path , locker == null ? transactionKey : locker);
        }
        catch( PersistencyException ex ) {
            Logger.error("ItemProxy.getObject() - Exception loading object:"+mItemPath+"/"+path);
            Logger.error(ex);
            throw new ObjectNotFoundException( ex.toString() );
        }
    }

    /**
     * Retrieves the values of a BuiltInItemProperty
     *
     * @param prop one of the Built-In Item Property
     * @return the value of the property
     * @throws ObjectNotFoundException property was not found
     */
    public String getProperty( BuiltInItemProperties prop ) throws ObjectNotFoundException {
        return getProperty(prop.getName());
    }

    /**
     * Retrieves the values of a BuiltInItemProperty or returns the defaulValue if no Property was found
     * 
     * @param prop one of the Built-In Item Property
     * @param defaultValue the value to be used if no Property was found
     * @return the value or the defaultValue
     */
    public String getProperty(BuiltInItemProperties prop, String defaultValue) {
        return getProperty(prop.getName(), defaultValue);
    }

    /**
     * Retrieves the values of a named property or returns the defaulValue if no Property was found
     * 
     * @param name of the Item Property
     * @param defaultValue the value to be used if no Property was found
     * @return the value or the defaultValue
     */
    public String getProperty(String name, String defaultValue) {
        return getProperty(name, defaultValue, transactionKey);
    }

    /**
     * Retrieves the value of a named property. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     *
     * @param name of the Item Property
     * @param defaultValue the value to be used if no Property was found
     * @param locker the transaction key
     * @return the value of the property
     */
    public String getProperty(String name, String defaultValue, Object locker) {
        try {
            if (checkContent(ClusterType.PROPERTY.getName(), name, locker == null ? transactionKey : locker)) {
                return getProperty(name, locker == null ? transactionKey : locker);
            }
        }
        catch(ObjectNotFoundException e) {
            //This line should never happen because of the use of checkContent()
        }

        return defaultValue;
    }

    /**
     * Retrieves the value of a named property
     *
     * @param name of the Item Property
     * @return the value of the property
     * @throws ObjectNotFoundException property was not found
     */
    public String getProperty( String name ) throws ObjectNotFoundException {
        return getProperty(name, (Object)null);
    }

    /**
     * 
     * @param name
     * @param locker
     * @return
     * @throws ObjectNotFoundException
     */
    public String getProperty(String name, Object locker) throws ObjectNotFoundException {
        Logger.msg(5, "ItemProxy.getProperty() - "+name+" from item "+mItemPath);

        Property prop = (Property)getObject(ClusterType.PROPERTY+"/"+name, locker == null ? transactionKey : locker);

        if(prop != null) return prop.getValue();
        else             throw new ObjectNotFoundException("ItemProxy.getProperty() - COULD not find property "+name+" from item "+mItemPath);
    }

    /**
     * Get the name of the Item from its Property called Name
     *
     * @return the name of the Item or null if no Name Property exists
     */
    public String getName() {
        return getProperty(NAME, (String)null);
    }

    /**
     * Get the type of the Item from its Property called Type
     *
     * @return the type of the Item or null if no Type Property exists
     */
    public String getType() {
        return getProperty(TYPE, (String)null);
    }

    /**
     * Retrieves the Event of the given id.
     * 
     * @param eventId the id of the Event
     * @return the Event object
     * @throws ObjectNotFoundException there is no event for the given id
     */
    public Event getEvent(int eventId) throws ObjectNotFoundException {
        return getEvent(eventId, transactionKey);
    }

    /**
     * Retrieves the Event of the given id. This method can be used in server side Script to find uncommitted changes
     * during the active transaction.
     * 
     * @param eventId the id of the Event
     * @param locker the transaction key
     * @return the Event object
     * @throws ObjectNotFoundException there is no event for the given id
     */
    public Event getEvent(int eventId, Object locker) throws ObjectNotFoundException {
        return (Event) getObject(HISTORY + "/" + eventId, locker == null ? transactionKey : locker);
    }

    //**************************************************************************
    // Subscription methods
    //**************************************************************************/


    public void subscribe(MemberSubscription<?> newSub) {
        newSub.setSubject(this);
        synchronized (this){
            mSubscriptions.put( newSub, newSub.getObserver() );
        }
        new Thread(newSub).start();
        Logger.msg(7, "ItemProxy.subscribe() - "+newSub.getObserver().getClass().getName()+" for "+newSub.interest);
    }

    public void unsubscribe(ProxyObserver<?> observer) {
        synchronized (this){
            for (Iterator<MemberSubscription<?>> e = mSubscriptions.keySet().iterator(); e.hasNext();) {
                MemberSubscription<?> thisSub = e.next();
                if (mSubscriptions.get( thisSub ) == observer) {
                    e.remove();
                    Logger.msg(7, "ItemProxy.unsubscribed() - "+observer.getClass().getName());
                }
            }
        }
    }

    public void dumpSubscriptions(int logLevel) {
        if(!Logger.doLog(logLevel) || mSubscriptions.size() == 0) return;

        Logger.msg(logLevel, "Subscriptions to proxy "+mItemPath+":");
        synchronized(this) {
            for (MemberSubscription<?> element : mSubscriptions.keySet()) {
                ProxyObserver<?> obs = element.getObserver();
                if (obs != null)
                    Logger.msg(logLevel, "    "+element.getObserver().getClass().getName()+" subscribed to "+element.interest);
                else
                    Logger.msg(logLevel, "    Phantom subscription to "+element.interest);
            }
        }
    }

    public void notify(ProxyMessage message) {
        Logger.msg(4, "ItemProxy.notify() - Received change notification for "+message.getPath()+" on "+mItemPath);
        synchronized (this){
            if (Gateway.getProxyServer()== null || !message.getServer().equals(Gateway.getProxyServer().getServerName())) {
                Gateway.getStorage().clearCache(mItemPath, message.getPath());
            }
            for (Iterator<MemberSubscription<?>> e = mSubscriptions.keySet().iterator(); e.hasNext();) {
                MemberSubscription<?> newSub = e.next();
                if (newSub.getObserver() == null) { // phantom
                    Logger.msg(4, "ItemProxy.notify() - Removing phantom subscription to "+newSub.interest);
                    e.remove();
                }
                else
                    newSub.update(message.getPath(), message.isState());
            }
        }
    }
}
