/*
 * This file is part of the 3D City Database Importer/Exporter.
 * Copyright (c) 2007 - 2012
 * Institute for Geodesy and Geoinformation Science
 * Technische Universitaet Berlin, Germany
 * http://www.gis.tu-berlin.de/
 * 
 * The 3D City Database Importer/Exporter program is free software:
 * you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program. If not, see 
 * <http://www.gnu.org/licenses/>.
 * 
 * The development of the 3D City Database Importer/Exporter has 
 * been financially supported by the following cooperation partners:
 * 
 * Business Location Center, Berlin <http://www.businesslocationcenter.de/>
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * Berlin Senate of Business, Technology and Women <http://www.berlin.de/sen/wtf/>
 */
package de.tub.citydb.modules.kml.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point3d;
import javax.xml.bind.JAXBException;

import net.opengis.kml._2.AltitudeModeEnumType;
import net.opengis.kml._2.LineStringType;
import net.opengis.kml._2.PlacemarkType;
import net.opengis.kml._2.PointType;
import oracle.jdbc.OracleResultSet;
import oracle.spatial.geometry.JGeometry;
import oracle.sql.STRUCT;

import org.citygml4j.factory.CityGMLFactory;

import de.tub.citydb.api.event.EventDispatcher;
import de.tub.citydb.config.Config;
import de.tub.citydb.config.project.kmlExporter.Balloon;
import de.tub.citydb.config.project.kmlExporter.ColladaOptions;
import de.tub.citydb.config.project.kmlExporter.DisplayForm;
import de.tub.citydb.log.Logger;
import de.tub.citydb.modules.common.event.CounterEvent;
import de.tub.citydb.modules.common.event.CounterType;
import de.tub.citydb.modules.common.event.GeometryCounterEvent;

public class Transportation extends KmlGenericObject{

	public static final String STYLE_BASIS_NAME = "Transportation";
	private boolean isTransportationComplex = false;

	public Transportation(Connection connection,
			KmlExporterManager kmlExporterManager,
			CityGMLFactory cityGMLFactory,
			net.opengis.kml._2.ObjectFactory kmlFactory,
			ElevationServiceHandler elevationServiceHandler,
			BalloonTemplateHandlerImpl balloonTemplateHandler,
			EventDispatcher eventDispatcher,
			Config config) {

		super(connection,
			  kmlExporterManager,
			  cityGMLFactory,
			  kmlFactory,
			  elevationServiceHandler,
			  balloonTemplateHandler,
			  eventDispatcher,
			  config);
	}

	protected List<DisplayForm> getDisplayForms() {
		return config.getProject().getKmlExporter().getTransportationDisplayForms();
	}

	public ColladaOptions getColladaOptions() {
		return config.getProject().getKmlExporter().getTransportationColladaOptions();
	}

	public Balloon getBalloonSettings() {
		return config.getProject().getKmlExporter().getTransportationBalloon();
	}

	public String getStyleBasisName() {
		return STYLE_BASIS_NAME;
	}

	protected String getHighlightingQuery() {
		return Queries.getTransportationHighlightingQuery(currentLod, isTransportationComplex);
	}

	public void read(KmlSplittingResult work) {

		PreparedStatement psQuery = null;
		OracleResultSet rs = null;
		
		isTransportationComplex = work.isTransportationComplex();
					 
		boolean reversePointOrder = false;

		try {
			int lodToExportFrom = config.getProject().getKmlExporter().getLodToExportFrom();
			currentLod = lodToExportFrom == 5 ? 4: lodToExportFrom;
			int minLod = lodToExportFrom == 5 ? 0: lodToExportFrom;

			while (currentLod >= minLod) {
				if(!work.getDisplayForm().isAchievableFromLoD(currentLod)) break;

				try {
					psQuery = connection.prepareStatement(Queries.getTransportationQuery(currentLod, work.getDisplayForm(), isTransportationComplex),
							   							  ResultSet.TYPE_SCROLL_INSENSITIVE,
							   							  ResultSet.CONCUR_READ_ONLY);

					for (int i = 1; i <= psQuery.getParameterMetaData().getParameterCount(); i++) {
						psQuery.setString(i, work.getGmlId());
					}
				
					rs = (OracleResultSet)psQuery.executeQuery();
					if (rs.isBeforeFirst()) {
						break; // result set not empty
					}
					else {
						try { rs.close(); /* release cursor on DB */ } catch (SQLException sqle) {}
						rs = null; // workaround for jdbc library: rs.isClosed() throws SQLException!
						try { psQuery.close(); /* release cursor on DB */ } catch (SQLException sqle) {}
					}
				}
				catch (Exception e2) {
					try { if (rs != null) rs.close(); } catch (SQLException sqle) {}
					rs = null; // workaround for jdbc library: rs.isClosed() throws SQLException!
					try { if (psQuery != null) psQuery.close(); } catch (SQLException sqle) {}
				}

				currentLod--;
			}

			if (rs == null) { // result empty, give up
				String fromMessage = lodToExportFrom == 5 ? " from any LoD": " from LoD" + lodToExportFrom;
				Logger.getInstance().info("Could not display object " + work.getGmlId() 
						+ " as " + work.getDisplayForm().getName() + fromMessage + ".");
			}
			else { // result not empty
				eventDispatcher.triggerEvent(new CounterEvent(CounterType.TOPLEVEL_FEATURE, 1, this));

				// get the proper displayForm (for highlighting)
				int indexOfDf = getDisplayForms().indexOf(work.getDisplayForm());
				if (indexOfDf != -1) {
					work.setDisplayForm(getDisplayForms().get(indexOfDf));
				}

				if (currentLod == 0) { // LoD0_Network
					kmlExporterManager.print(createPlacemarksForLoD0Network(rs, work),
							 				 work,
							 				 getBalloonSettings().isBalloonContentInSeparateFile());
				}
				else {
					switch (work.getDisplayForm().getForm()) {
					case DisplayForm.FOOTPRINT:
						kmlExporterManager.print(createPlacemarksForFootprint(rs, work),
												 work,
												 getBalloonSettings().isBalloonContentInSeparateFile());
						break;
					case DisplayForm.EXTRUDED:
	
						PreparedStatement psQuery2 = connection.prepareStatement(Queries.GET_EXTRUDED_HEIGHT);
						for (int i = 1; i <= psQuery2.getParameterMetaData().getParameterCount(); i++) {
							psQuery2.setString(i, work.getGmlId());
						}
						OracleResultSet rs2 = (OracleResultSet)psQuery2.executeQuery();
						rs2.next();
						double measuredHeight = rs2.getDouble("envelope_measured_height");
						try { rs2.close(); /* release cursor on DB */ } catch (SQLException e) {}
						try { psQuery2.close(); /* release cursor on DB */ } catch (SQLException e) {}
						
						kmlExporterManager.print(createPlacemarksForExtruded(rs, work, measuredHeight, reversePointOrder),
												 work,
												 getBalloonSettings().isBalloonContentInSeparateFile());
						break;
					case DisplayForm.GEOMETRY:
						if (config.getProject().getKmlExporter().getFilter().isSetComplexFilter()) { // region
							if (work.getDisplayForm().isHighlightingEnabled()) {
								kmlExporterManager.print(createPlacemarksForHighlighting(work),
														 work,
														 getBalloonSettings().isBalloonContentInSeparateFile());
							}
							kmlExporterManager.print(createPlacemarksForGeometry(rs, work),
													 work,
													 getBalloonSettings().isBalloonContentInSeparateFile());
						}
						else { // reverse order for single buildings
							kmlExporterManager.print(createPlacemarksForGeometry(rs, work),
													 work,
													 getBalloonSettings().isBalloonContentInSeparateFile());
	//							kmlExporterManager.print(createPlacemarkForEachSurfaceGeometry(rs, work.getGmlId(), false));
							if (work.getDisplayForm().isHighlightingEnabled()) {
	//							kmlExporterManager.print(createPlacemarkForEachHighlingtingGeometry(work),
	//							 						 work,
	//							 						 getBalloonSetings().isBalloonContentInSeparateFile());
								kmlExporterManager.print(createPlacemarksForHighlighting(work),
														 work,
														 getBalloonSettings().isBalloonContentInSeparateFile());
							}
						}
						break;
					case DisplayForm.COLLADA:
						fillGenericObjectForCollada(rs, work.getGmlId());
						List<Point3d> anchorCandidates = setOrigins(); // setOrigins() called mainly for the side-effect
						double zOffset = getZOffsetFromConfigOrDB(work.getGmlId());
						if (zOffset == Double.MAX_VALUE) {
							zOffset = getZOffsetFromGEService(work.getGmlId(), anchorCandidates);
						}
						setZOffset(zOffset);
	
						ColladaOptions colladaOptions = getColladaOptions();
						setIgnoreSurfaceOrientation(colladaOptions.isIgnoreSurfaceOrientation());
						try {
							if (work.getDisplayForm().isHighlightingEnabled()) {
	//							kmlExporterManager.print(createPlacemarkForEachHighlingtingGeometry(work),
	//													 work,
	//													 getBalloonSetings().isBalloonContentInSeparateFile());
								kmlExporterManager.print(createPlacemarksForHighlighting(work),
														 work,
														 getBalloonSettings().isBalloonContentInSeparateFile());
							}
						}
						catch (Exception ioe) {
							ioe.printStackTrace();
						}
	
						break;
					}
				}
			}
		}
		catch (SQLException sqlEx) {
			Logger.getInstance().error("SQL error while querying city object " + work.getGmlId() + ": " + sqlEx.getMessage());
			return;
		}
		catch (JAXBException jaxbEx) {
			return;
		}
		finally {
			if (rs != null)
				try { rs.close(); } catch (SQLException e) {}
			if (psQuery != null)
				try { psQuery.close(); } catch (SQLException e) {}
		}
	}

	public PlacemarkType createPlacemarkForColladaModel() throws SQLException {
		// undo trick for very close coordinates
		double[] originInWGS84 = convertPointCoordinatesToWGS84(new double[] {getOriginX()/100, getOriginY()/100, getOriginZ()/100});
		setLocationX(reducePrecisionForXorY(originInWGS84[0]));
		setLocationY(reducePrecisionForXorY(originInWGS84[1]));
		setLocationZ(reducePrecisionForZ(originInWGS84[2]));

		return super.createPlacemarkForColladaModel();
	}

	private List<PlacemarkType> createPlacemarksForLoD0Network(OracleResultSet rs,
															   KmlSplittingResult work) throws SQLException {

		DisplayForm footprintSettings = new DisplayForm(DisplayForm.FOOTPRINT, -1, -1);
		int indexOfDf = getDisplayForms().indexOf(footprintSettings);
		if (indexOfDf != -1) {
			footprintSettings = getDisplayForms().get(indexOfDf);
		}

		List<PlacemarkType> placemarkList= new ArrayList<PlacemarkType>();

		while (rs.next()) {
			PlacemarkType placemark = kmlFactory.createPlacemarkType();
			placemark.setName(work.getGmlId() + "_Transportation_Network");
			placemark.setId(/* DisplayForm.FOOTPRINT_PLACEMARK_ID + */ placemark.getName());
			if (footprintSettings.isHighlightingEnabled())
				placemark.setStyleUrl("#" + getStyleBasisName() + DisplayForm.FOOTPRINT_STR + "Style");
			else
				placemark.setStyleUrl("#" + getStyleBasisName() + DisplayForm.FOOTPRINT_STR + "Normal");
			
			STRUCT buildingGeometryObj = (STRUCT)rs.getObject(1); 
			JGeometry pointOrCurveGeometry = JGeometry.load(buildingGeometryObj);
			eventDispatcher.triggerEvent(new GeometryCounterEvent(null, this));

			if (pointOrCurveGeometry.isPoint()) { // point
				double[] ordinatesArray = pointOrCurveGeometry.getPoint();
				ordinatesArray = super.convertPointCoordinatesToWGS84(ordinatesArray);

				PointType point = kmlFactory.createPointType();
				point.getCoordinates().add(String.valueOf(reducePrecisionForXorY(ordinatesArray[0]) + "," 
														+ reducePrecisionForXorY(ordinatesArray[1]) + ","
														+ reducePrecisionForZ(ordinatesArray[2])));

				placemark.setAbstractGeometryGroup(kmlFactory.createPoint(point));
			}
			else { // curve
				pointOrCurveGeometry = super.convertToWGS84(pointOrCurveGeometry);
				double[] ordinatesArray = pointOrCurveGeometry.getOrdinatesArray();

				LineStringType lineString = kmlFactory.createLineStringType();
				for (int i = 0; i < pointOrCurveGeometry.getElemInfo().length; i = i+3) {
					int startNextRing = ((i+3) < pointOrCurveGeometry.getElemInfo().length) ? 
										pointOrCurveGeometry.getElemInfo()[i+3] - 1: // still holes to come
										ordinatesArray.length; // default

					// order points clockwise
					for (int j = pointOrCurveGeometry.getElemInfo()[i] - 1; j < startNextRing; j = j+3) {
						lineString.getCoordinates().add(String.valueOf(reducePrecisionForXorY(ordinatesArray[j]) + "," 
																	 + reducePrecisionForXorY(ordinatesArray[j+1]) + ","
																	 + reducePrecisionForZ(ordinatesArray[j+2])));
					}
				}
				lineString.setAltitudeModeGroup(kmlFactory.createAltitudeMode(AltitudeModeEnumType.CLAMP_TO_GROUND));
				placemark.setAbstractGeometryGroup(kmlFactory.createLineString(lineString));
			}
			// replace default BalloonTemplateHandler with a brand new one, this costs resources!
			if (getBalloonSettings().isIncludeDescription()) {
				addBalloonContents(placemark, work.getGmlId());
			}
			placemarkList.add(placemark);
		}
		return placemarkList;
	}

}