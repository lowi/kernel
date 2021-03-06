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
package org.cristalise.kernel.lifecycle.renderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.util.ArrayList;

import org.cristalise.kernel.graph.model.Vertex;
import org.cristalise.kernel.graph.renderer.DefaultVertexRenderer;
import org.cristalise.kernel.lifecycle.ActivitySlotDef;
import org.cristalise.kernel.lifecycle.WfVertexDef;

/**
 * Renders elementary ActivityDef
 */
public class ActivityDefRenderer extends DefaultVertexRenderer {
    private Paint mInactivePaint  = Color.WHITE;
    private Paint mErrorPaint     = new Color(255, 50,  0);
    private Paint mCompositePaint = new Color(200, 200, 255);
    private Paint mTextPaint      = Color.black;

    /**
     * Draws the ActivityDef as a 3D rectangle without borders, with text lines for Name, DefinitionName and errors
     */
    @Override
    public void draw(Graphics2D g2d, Vertex vertex) {
        WfVertexDef activityDef = (WfVertexDef) vertex;
        boolean hasError = !activityDef.verify();

        Paint fillPaint = hasError ? mErrorPaint : activityDef.getIsComposite() ? mCompositePaint : mInactivePaint;
        drawOutline3DRect(g2d, vertex, fillPaint);

        ArrayList<String> linesOfText = new ArrayList<String>();

        if (activityDef instanceof ActivitySlotDef) {
            try {
                linesOfText.add((String) activityDef.getProperties().get("Name"));
                linesOfText.add("(" + ((ActivitySlotDef) activityDef).getTheActivityDef().getActName() + ")");
            }
            catch (Exception e) {
                linesOfText.add("(Not found)");
            }
        }
        else
            linesOfText.add(activityDef.getName());

        if (hasError) linesOfText.add(activityDef.getErrors());

        drawLinesOfTexts(g2d, activityDef, linesOfText, mTextPaint);
    }
}
