/*******************************************************************************
 * Copyright (C) 2026, 1C-Soft LLC and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     1C-Soft LLC - initial API and implementation
 *******************************************************************************/
package com.e1c.v8codestyle.bsl.fix.itests;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com._1c.g5.v8.dt.validation.marker.Marker;
import com.e1c.v8codestyle.bsl.check.NotifyDescriptionToServerProcedureCheck;

/**
 *  Tests for NotifyDescriptionToServerProcedureFix fix.
 *
 *  @author Artem Samohvalov
 */
public class NotifyDescriptionToServerProcedureFixTest
    extends AbstractQuickFixTest
{
    public NotifyDescriptionToServerProcedureFixTest()
    {
        super(NotifyDescriptionToServerProcedureCheck.class);
    }

    @Test
    public void testApplyFix() throws Exception
    {
        updateModule(FOLDER_RESOURCE + "fix-notify-description-to-server-procedure.bsl");

        List<Marker> markers = getModuleMarkers();
        assertEquals(1, markers.size());
        Marker marker = markers.get(0);

        performFix(marker, "Создать отсутствующую процедуру для ОписаниеОповещения");
        assertMarkerGone();
    }

}
