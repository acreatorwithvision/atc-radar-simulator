package com.atc.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Generates realistic-ish aircraft for the simulation. */
public class AircraftFactory {

    private static final Random RNG = new Random();

    private static final String[] AIRLINES  = {"UAL","AAL","DAL","SWA","JBU","SKW","FFT","NKS"};
    private static final String[] AC_TYPES  = {"B738","A320","B77W","A321","CRJ9","E175","B739","A319"};
    private static final String[] AIRPORTS  = {"KLAX","KJFK","KORD","KATL","KDFW","KDEN","KSFO","KMIA"};
    private static final int[]    ALTITUDES = {8000,10000,12000,15000,18000,22000,25000,28000,33000,37000};
    private static final int[]    SPEEDS    = {220, 250, 270, 290, 310, 340, 360, 380};

    /** Spawn N aircraft scattered across the radar scope. */
    public static List<Aircraft> createInitialTraffic(int count) {
        List<Aircraft> list = new ArrayList<>(count);
        double cx = RadarCanvas.CENTER_X;
        double cy = RadarCanvas.CENTER_Y;
        double maxR = RadarCanvas.MAX_RADIUS * 0.75; // spawn within 75% of scope

        for (int i = 0; i < count; i++) {
            String callsign = AIRLINES[RNG.nextInt(AIRLINES.length)]
                            + (100 + RNG.nextInt(900));

            // Random position within radar circle
            double angle  = RNG.nextDouble() * 2 * Math.PI;
            double radius = RNG.nextDouble() * maxR;
            double x      = cx + radius * Math.cos(angle);
            double y      = cy + radius * Math.sin(angle);

            double heading  = RNG.nextDouble() * 360;
            int    altitude = ALTITUDES[RNG.nextInt(ALTITUDES.length)];
            int    speed    = SPEEDS[RNG.nextInt(SPEEDS.length)];
            String type     = AC_TYPES[RNG.nextInt(AC_TYPES.length)];
            String origin   = AIRPORTS[RNG.nextInt(AIRPORTS.length)];
            String dest     = AIRPORTS[RNG.nextInt(AIRPORTS.length)];

            list.add(new Aircraft(callsign, type, origin, dest, x, y, heading, altitude, speed));
        }
        return list;
    }

    /** Spawn a single random aircraft at the radar edge. */
    public static Aircraft spawnAtEdge() {
        double edgeAngle = RNG.nextDouble() * 2 * Math.PI;
        double r         = RadarCanvas.MAX_RADIUS * 0.88;
        double x         = RadarCanvas.CENTER_X + r * Math.cos(edgeAngle);
        double y         = RadarCanvas.CENTER_Y + r * Math.sin(edgeAngle);

        // Head roughly toward center
        double headingToCenter = Math.toDegrees(
            Math.atan2(RadarCanvas.CENTER_Y - y, RadarCanvas.CENTER_X - x)
        ) + 90 + (RNG.nextDouble() * 40 - 20);

        String callsign = AIRLINES[RNG.nextInt(AIRLINES.length)]
                        + (100 + RNG.nextInt(900));
        String type     = AC_TYPES[RNG.nextInt(AC_TYPES.length)];
        String origin   = AIRPORTS[RNG.nextInt(AIRPORTS.length)];
        String dest     = AIRPORTS[RNG.nextInt(AIRPORTS.length)];
        int    altitude = ALTITUDES[RNG.nextInt(ALTITUDES.length)];
        int    speed    = SPEEDS[RNG.nextInt(SPEEDS.length)];

        return new Aircraft(callsign, type, origin, dest,
                            x, y, headingToCenter, altitude, speed);
    }
}
