package com.janakiev.minimal.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.BBox;
import com.janakiev.minimal.MinimalGraphHopper;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import static com.graphhopper.util.Helper.UTF_CS;

public class MinimalGraphHopperManaged implements Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    public MinimalGraphHopperManaged(CmdArgs configuration, ObjectMapper objectMapper) {
        String splitAreaLocation = configuration.get(Parameters.Landmark.PREPARE + "split_area_location", "");
        JsonFeatureCollection landmarkSplittingFeatureCollection;
        try (Reader reader = splitAreaLocation.isEmpty() ? new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream(), UTF_CS) : new InputStreamReader(new FileInputStream(splitAreaLocation), UTF_CS)) {
            landmarkSplittingFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection.class);
        } catch (IOException e1) {
            logger.error("Problem while reading border map GeoJSON. Skipping this.", e1);
            landmarkSplittingFeatureCollection = null;
        }
        
        graphHopper = new MinimalGraphHopper(landmarkSplittingFeatureCollection).forServer();
        //graphHopper = new GraphHopperOSM(landmarkSplittingFeatureCollection).forServer();
        String spatialRuleLocation = configuration.get("spatial_rules.location", "");
        if (!spatialRuleLocation.isEmpty()) {
            final BBox maxBounds = BBox.parseBBoxString(configuration.get("spatial_rules.max_bbox", "-180, 180, -90, 90"));
            try (final InputStreamReader reader = new InputStreamReader(new FileInputStream(spatialRuleLocation), UTF_CS)) {
                JsonFeatureCollection jsonFeatureCollection = objectMapper.readValue(reader, JsonFeatureCollection.class);
                SpatialRuleLookupHelper.buildAndInjectSpatialRuleIntoGH(graphHopper, maxBounds, jsonFeatureCollection);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        graphHopper.init(configuration);
    }

    @Override
    public void start() {
        graphHopper.importOrLoad();
        logger.info("loaded graph at:" + graphHopper.getGraphHopperLocation()
                + ", data_reader_file:" + graphHopper.getDataReaderFile()
                + ", flag_encoders:" + graphHopper.getEncodingManager()
                + ", " + graphHopper.getGraphHopperStorage().toDetailsString());
    }

    GraphHopper getGraphHopper() {
        return graphHopper;
    }

    @Override
    public void stop() {
        graphHopper.close();
    }
}