/*
 * h2spatial is a library that brings spatial support to the H2 Java database.
 *
 * h2spatial is distributed under GPL 3 license. It is produced by the "Atelier SIG"
 * team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR 2488.
 *
 * Copyright (C) 2007-2014 IRSTV (FR CNRS 2488)
 *
 * h2patial is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * h2spatial is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * h2spatial. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.h2gis.h2spatialext.drivers.osm;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

/**
 * A class to manage the node element properties.
 *
 * @author Erwan Bocher
 */
public class NodeOSMElement extends OSMElement {

    private Point point;

    public NodeOSMElement() {
        super();
    }

    /**
     * The geometry of the node
     *
     * @return
     */
    public Point getPoint() {
        return point;
    }

    /**
     * Create a new geometry point based on the latitude and longitude values
     *
     * @param gf
     * @param lon
     * @param lat
     */
    public void createPoint(GeometryFactory gf, String lon, String lat) {
        point = gf.createPoint(new Coordinate(Double.valueOf(lon),
                Double.valueOf(lat)));
    }

}