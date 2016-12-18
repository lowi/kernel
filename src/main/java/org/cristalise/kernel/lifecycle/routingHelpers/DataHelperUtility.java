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
package org.cristalise.kernel.lifecycle.routingHelpers;

import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.common.PersistencyException;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.utils.Logger;

/**
 * Utility class to retrieve and resolve DataHelpers
 */
public class DataHelperUtility {

    /**
     * First checks the configuration properties to instantiate the requested Datahelper.
     * If there is such no property, it uses the given id to instantiate one of these classes:
     * {@link ViewpointDataHelper}, {@link PropertyDataHelper}, {@link ActivityDataHelper}
     * 
     * @param id the string user to identify the DataHelper in the cristal-ise configuration
     * @return the DataHelper instance
     * @throws InvalidDataException could not configure DataHelper
     */
    public static DataHelper getDataHelper(String id) throws InvalidDataException {
        Object configHelper = Gateway.getProperties().getObject("DataHelper."+id);

        if (configHelper != null) {
            if (configHelper instanceof DataHelper) 
                return (DataHelper)configHelper;
            else 
                throw new InvalidDataException("Config value is not an instance of DataHelper - 'DataHelper."+id+"'=" +configHelper.toString());
        }
        else {
            switch (BuiltInDataHelpers.getValue(id)) {
                case VIEWPOINT_DH:
                    return new ViewpointDataHelper();
                case PROPERTY_DH:
                    return new PropertyDataHelper();
                case ACTIVITY_DH:
                    return new ActivityDataHelper();
                default:
                    Logger.warning("DataHelperUtility.getDataHelper() - UNKOWN DataHelper id:"+id);
            }
        }
        return null;
    }

    /**
     * If the 
     * 
     * @param itemPath the actual Item context
     * @param value the value to be evaluated
     * @param actContext activity path
     * @param locker database transaction locker
     * @return String value which was evaluated using {@link DataHelper} implementation
     * 
     * @throws InvalidDataException data inconsistency
     * @throws PersistencyException persistency issue
     * @throws ObjectNotFoundException  object was not found
     */
    public static Object evaluateValue(ItemPath itemPath, Object value, String actContext, Object locker) 
            throws InvalidDataException, PersistencyException, ObjectNotFoundException
    {
        if (value == null || !(value instanceof String) || !((String)value).contains("//"))
            return value;

        if(itemPath == null) throw new InvalidDataException("DataHelper must have ItemPath initialised");

        String[] valueSplit = ((String)value).split("//");

        if (valueSplit.length != 2) throw new InvalidDataException("DataHelperUtility.evaluateValue() - Too many '//' in value:"+value);

        String pathType = valueSplit[0];
        String dataPath = valueSplit[1];
        
        Logger.msg(5, "DataHelperUtility.evaluateValue() - pathType:"+pathType+" dataPath:"+dataPath);

        DataHelper dataHelper = getDataHelper(pathType);

        if (dataHelper != null) return dataHelper.get(itemPath, actContext, dataPath, locker);
        else                    return value;
    }
}
