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
package org.cristalise.kernel.lifecycle.instance.predefined;

import static org.cristalise.kernel.graph.model.BuiltInVertexProperties.MEMBER_ADD_SCRIPT;

import java.util.Arrays;

import org.cristalise.kernel.collection.Dependency;
import org.cristalise.kernel.collection.DependencyMember;
import org.cristalise.kernel.common.InvalidCollectionModification;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.ObjectAlreadyExistsException;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.common.PersistencyException;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.utils.CastorHashMap;

/**
 * <pre>
 * Generates a new slot in a Dependency for the given item
 * 
 * Params:
 * 0 - collection name
 * 1 - target UUID or DomainPath
 * 2 - slot properties (optional)
 * </pre>
 */
public class AddMemberToCollection extends PredefinedStepCollectionBase {
    public static final String description = "Creates a new member slot for the given item in a dependency, and assigns the item";
    
    /**
     * Constructor for Castor
     */
    public AddMemberToCollection() {
        super();
    }

    @Override
    protected String runActivityLogic(AgentPath agent, ItemPath item, int transitionID, String requestData, Object locker)
            throws InvalidDataException, ObjectAlreadyExistsException, PersistencyException, ObjectNotFoundException, InvalidCollectionModification
    {
        String[] params = unpackParamsAndGetCollection(item, requestData, locker);

        Dependency dep = getDependency();
        DependencyMember member = null;

        // find member and assign entity
        if (memberNewProps == null) member = dep.createMember(childPath);
        else                        member = dep.createMember(childPath, memberNewProps);

        if (dep.containsBuiltInProperty(MEMBER_ADD_SCRIPT)) {
            CastorHashMap scriptProps = new CastorHashMap();
            scriptProps.put("collection", dep);
            scriptProps.put("member", member);

            evaluateScript(item, (String)dep.getBuiltInProperty(MEMBER_ADD_SCRIPT), scriptProps, locker);
        }

        dep.addMember(member);

        Gateway.getStorage().put(item, dep, locker);

        //put ID of the newly created member into the return data of this step
        params = Arrays.copyOf(params, params.length+1);
        params[params.length-1] = Integer.toString(member.getID());

        return bundleData(params);
    }
}
