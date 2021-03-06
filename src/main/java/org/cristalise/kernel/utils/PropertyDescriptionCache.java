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
/**
 *
 */
package org.cristalise.kernel.utils;

import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.process.resource.BuiltInResources;
import org.cristalise.kernel.property.PropertyDescriptionList;

public class PropertyDescriptionCache extends DescriptionObjectCache<PropertyDescriptionList> {

    @Override
    public String getTypeCode() {
        return BuiltInResources.PROPERTY_DESC_RESOURCE.getTypeCode();
    }

    @Override
    public String getSchemaName() {
        return BuiltInResources.PROPERTY_DESC_RESOURCE.getSchemaName();
    }

    @Override
    public PropertyDescriptionList buildObject(String name, int version, ItemPath path, String data) throws InvalidDataException {
        try {
            PropertyDescriptionList pdl = (PropertyDescriptionList) Gateway.getMarshaller().unmarshall(data);
            pdl.setName(name);
            pdl.setVersion(version);
            pdl.setItemPath(path);
            return pdl;
        }
        catch (Exception ex) {
            Logger.error(ex);
            throw new InvalidDataException("Could not parse Schema '" + name + "' v" + version + ": " + ex.getMessage());
        }
    }
}