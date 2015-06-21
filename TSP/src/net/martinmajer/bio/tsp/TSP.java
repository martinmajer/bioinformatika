package net.martinmajer.bio.tsp;

import java.awt.*;
import javax.swing.*;
import java.util.*;

/**
 * Problém obchodního cestujícího - řešení pomocí mravenčí kolonie.
 * @author Martin
 */
public class TSP extends JFrame {
    
    // rozměry plátna
    private static final int CANVAS_WIDTH = 1200;
    private static final int CANVAS_HEIGHT = 800;
    
    
    /** Maximální počet iterací algoritmu. */
    private static final int MAX_ITERATIONS = 100000;
    
    /** Životnost feromonů (poločas rozpadu v iteracích). */
    private static final int PHEROMONES_HALF_LIFE = 1000;
    
    /** Omezení počtu pohybů (počet měst * MAX_MOVES_FACTOR) jedné iterace */
    private static final int   MAX_MOVES_FACTOR = 8;
    
    /** Konstanta pro započátání délky cesty. */
    private static final float DISTANCE_FACTOR = -1f;
    
    /** Konstanta pro započítání počtu feromonů. */
    private static final float PHEROMONE_FACTOR = 1f;
    
    /** Penalizace za navštívení jednoho města 2x. */
    private static final float VISITED_PENALISATION = -2;
    
    /** Penalizace za použití stejné cesty 2x. */
    private static final float VISITED_ROAD_PENALISATION = -1;
    
    /** Penalizace za návrat stejnou cestu. */
    private static final float RETURN_SAME_WAY_PENALISATION = -1;
    
    
    /** Pravděpodobnost, že mravenec půjde náhodným směrem. */
    private static final float RANDOM_PROBABILITY = 0.01f;
    
    /** Přičtení náhody. */
    private static final float RANDOM_FACTOR = 100;
    
    
    /** Bonus za feromony u nejlepšího mravence - kolik % z celkového množství na mapě. */
    private static final float BEST_DISTANCE_FACTOR = 0.66f;
    
    
    // konstanty pro generování náhodných dat
    private static final long RANDOM_DATA_SEED = 1L;
    
    private static final int RANDOM_DATA_MIN_CITIES = 25;
    private static final boolean RANDOM_DATA_CONNECT_ALL_CITIES = false;
    private static final int RANDOM_DATA_MIN_CITY_DISTANCE = 50;
    private static final float RANDOM_DATA_ROAD_PROBABILITY = 0.1f;
    private static final int RANDOM_DATA_CONNECT_DISTANCE = 400;
    
    // ostatní konstanty
    private static final boolean DEBUG_PRINT = false;
    private static final long SLEEP_TIME = 0;
    
    
    /** Mapa měst. */
    private CityMap map;
    
    /** Počet iterací (mravenců). */
    private int iterations = 0;
    
    /** Počet ignorovaných iterací. */
    private int ignored = 0;
    
    /** Nejkratší uražená vzdálenost. */
    private float minDistance = -1;
    
    /** Iterace, ve které byla nalezena nejkratší cesta. */
    private int minDistanceIteration = 0;
    
    
    /** Nejkratší cesta. */
    private Set <Road> bestRoadsSet = null;
    
    
    public TSP() {
        super("Ant colony");
        
        getContentPane().setLayout(new BorderLayout(0, 0));
        
        TSPPanel tspPanel = new TSPPanel();
        tspPanel.setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
        getContentPane().add(tspPanel, BorderLayout.CENTER);
        pack();
                
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }
    
    /** Spustí simulaci mravenční kolonie. */
    public void runSimulation() {
        while (iterations < MAX_ITERATIONS) {
            if (SLEEP_TIME > 0) {
                try {
                    Thread.sleep(SLEEP_TIME);
                }
                catch (InterruptedException e) {}
            }
            
            runOneIteration();
            repaint();
        }
    }
    
    
    /** Spustí jednu iteraci algoritmu. */
    public void runOneIteration() {
        iterations++;
        
        // jednou za čas feromony vydělíme dvěma a přičteme čekající
        if (iterations % PHEROMONES_HALF_LIFE == 0) {
            double totalPheromones = 0;
            double totalPending = 0;
            
            for (Road r: map.roads) {
                r.pheromone /= 2;
                r.pheromone += r.pheromonePending;
                r.pheromonePending = 0;
                
                totalPheromones += r.pheromone;
                totalPending += r.pheromonePending;
            }
            
            map.totalPheromones = totalPheromones;
            map.totalPheromonesPending = totalPending;
        }
        
        // náhodný výběr počátečního města
        City startCity = map.cities.get((int)(Math.random() * map.cities.size()));
        
        float totalRoadsDistance = map.sumRoadDistances();
        float avgDistance = totalRoadsDistance / map.roads.size();
        
        // množina navštívených měst
        Set <City> visitedCities = new HashSet();
        
        // pole a množina cest
        ArrayList <Road> roadsTravelledList = new ArrayList();
        Set <Road> roadsTravelledSet = new HashSet();
        float totalDistanceTravelled = 0;   // uražená vzdálenost
        
        // maximální počet pohybů - omezení, aby mravenec nechodil donekonečna
        int maxMoves = map.cities.size() * MAX_MOVES_FACTOR, moves = 0;
        
        City currentCity = startCity;
        if (DEBUG_PRINT) System.out.print(currentCity.name + " ");
        
        // mravenec bude chodit po mapě, dokud nenavštívil všechna města, nebo není ve výchozím městě
        while ((visitedCities.size() < map.cities.size() || currentCity != startCity) && moves++ < maxMoves) {
            ArrayList <Road> possibleRoads = currentCity.roads;
            
            // vybere cestu s nejvyšším ohodnocením
            Road selectedRoad = null;
            float maxRating = -10000;
            
            for (Road r: possibleRoads) {
                float rating = 0;
                City opposite = r.getOppositeCity(currentCity);
                
                // postih za dlouhé cesty (obvykle -0.5 až -2)
                rating += DISTANCE_FACTOR * Math.sqrt(r.distance / avgDistance);
                
                // bonus za feromony (obvykle 0 - 2)
                if (map.totalPheromones > 0) {
                    rating += PHEROMONE_FACTOR * (float)(r.pheromone / map.totalPheromones * map.cities.size() / (r.distance / avgDistance)); // 1 pro nejlepší trasu
                }
                
                // postihy za cesty po stejných cestách / do navštívených míst
                if (visitedCities.contains(opposite)) rating += VISITED_PENALISATION;
                if (roadsTravelledSet.contains(r)) {
                    rating += VISITED_ROAD_PENALISATION;
                    if (r == roadsTravelledList.get(roadsTravelledList.size() - 1)) {
                        rating += RETURN_SAME_WAY_PENALISATION;
                    }
                }
                
                // vliv náhody
                if (Math.random() < RANDOM_PROBABILITY) rating += RANDOM_FACTOR * (Math.random() - 0.5);
                
                // výběr cesty s největším ohodnocením
                if (rating > maxRating) {
                    selectedRoad = r;
                    maxRating = rating;
                }
            }
            
            roadsTravelledList.add(selectedRoad);
            roadsTravelledSet.add(selectedRoad);
            totalDistanceTravelled += selectedRoad.distance;
            
            currentCity = selectedRoad.getOppositeCity(currentCity);
            visitedCities.add(currentCity);
            
            if (DEBUG_PRINT) System.out.print(currentCity.name + " ");
        }
        
        if (DEBUG_PRINT) System.out.println();
        
        if (visitedCities.size() < map.cities.size() || currentCity != startCity) {
            if (DEBUG_PRINT) System.out.printf("Iteration #%d ignored\n", iterations);
            ignored++;
            
            return;
        }
        
        
        // dostupné feromony
        double pheromonesAvailable = map.cities.size();

        // výrazná penalizace za moc pohybů
        if (moves > map.cities.size()) {
            pheromonesAvailable /= (moves - map.cities.size());
        }

        // bonus za nejkratší řešení
        if (totalDistanceTravelled < minDistance) {
            pheromonesAvailable += (map.totalPheromones + map.totalPheromonesPending) * BEST_DISTANCE_FACTOR;
        }

        // feromony vydělíme vzdáleností - vycházíme z toho, že mravenec pohybující se
        // po kratším okruhu stihne urazit cestu několikrát, tudíž uloží víc feromonů
        double pheromonesPerUnit = pheromonesAvailable / totalDistanceTravelled;

        // přiřadíme feromony do mapy - jako čekající
        for (Road r: roadsTravelledList) {
            r.pheromonePending += pheromonesPerUnit * r.distance;
        }
        map.totalPheromonesPending += pheromonesAvailable;
        
        // aktualizace případné nejkratší cesty
        if (minDistance < 0 || totalDistanceTravelled < minDistance) {
            minDistance = totalDistanceTravelled;
            bestRoadsSet = roadsTravelledSet;
            minDistanceIteration = iterations;
        }

        if (DEBUG_PRINT) System.out.printf("Finished iteration #%d, moves = %d, distance = %.2f, pheromones = %.2f\n", iterations, moves, totalDistanceTravelled, pheromonesAvailable);
    }
    
    
    /** Inicializuje náhodná data. */
    public void initRandomData() {
        Random random = new Random(RANDOM_DATA_SEED);
        
        map = new CityMap();
        int nCities = (int)(random.nextFloat() * 10) + RANDOM_DATA_MIN_CITIES;
        
        // vygenerování zadaného počtu měst
        for (int i = 0; i < nCities; i++) {
            City newCity;
            boolean tooClose;
            
            // generujeme město do té doby, dokud je v dostatečné vzdálenosti od ostatních měst
            do {
                newCity = new City(
                        (int)(random.nextFloat() * (CANVAS_WIDTH - 50)) + 25, 
                        (int)(random.nextFloat() * (CANVAS_HEIGHT - 50)) + 25, 
                        Integer.toHexString(i).toUpperCase()
                );
                tooClose = false;
                for (City other: map.cities) {
                    if (City.distance(newCity, other) < RANDOM_DATA_MIN_CITY_DISTANCE) {
                        tooClose = true;
                        break;
                    }
                }
            }
            while (tooClose);
            
            map.cities.add(newCity);
        }
        
        // propojení měst mezi sebou
        for (int i = 0; i < nCities; i++) {
            for (int j = i + 1; j < nCities; j++) {
                City a = map.cities.get(i);
                City b = map.cities.get(j);
                
                if (RANDOM_DATA_CONNECT_ALL_CITIES || random.nextFloat() < RANDOM_DATA_ROAD_PROBABILITY || City.distance(a, b) < RANDOM_DATA_CONNECT_DISTANCE) {
                    map.connectCities(a, b);
                }
            }
        }
    }
    
    
    /** Panel pro vykreslování mapy / grafu. */
    class TSPPanel extends JPanel {
        
        @Override
        public void paint(Graphics g) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
            
            
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
            FontMetrics fm = g.getFontMetrics();
            
            double totalDistance = map.sumRoadDistances();
            
            // vykreslení cest
            for (Road r: map.roads) {
                // výpočet barvy cesty
                float gval = 0;
                if (map.totalPheromones > 0) gval = (float)((r.pheromone / map.totalPheromones) / (r.distance / totalDistance) - 0.5f) * 0.9f;
                if (gval > 0.9f) gval = 0.9f;
                if (gval < 0) gval = 0;
                
                g.setColor(new Color(0.9f - gval, 0.9f, 0.9f - gval));
                
                // zvýraznění hrany nejkratší cesty
                if (bestRoadsSet != null && bestRoadsSet.contains(r)) {
                    ((Graphics2D)g).setStroke(new BasicStroke(5));
                }
                else {
                    ((Graphics2D)g).setStroke(new BasicStroke(1));
                }
                
                // nakreslení čáry
                g.drawLine((int)r.a.x, (int)r.a.y, (int)r.b.x, (int)r.b.y);
                
                // hodnota feromonů u důležitých cest
                if (gval > 0.6 || (bestRoadsSet != null && bestRoadsSet.contains(r))) {
                    g.setColor(Color.BLACK);
                    String strToDraw = String.format("%.1f%%", r.pheromone / map.totalPheromones * 100);
                    g.drawString(strToDraw, (int)((r.a.x + r.b.x - fm.stringWidth(strToDraw))/2), (int)((r.a.y + r.b.y + fm.getHeight())/2));
                }
            }
            
            
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            
            // nakreslení měst
            for (City c: map.cities) {
                g.setColor(Color.RED);
                g.fillRect((int)c.x - 4, (int)c.y - 4, 9, 9);
                
                g.setColor(Color.BLACK);
                g.drawString(c.name, (int)c.x + 10, (int)c.y + 20);
            }
            
            // nakreslení informací
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            g.drawString(String.format("Nodes = %d, edges = %d", map.cities.size(), map.roads.size()), 10, getHeight() - 55);
            g.drawString(String.format("Iterations = %d (%d)", iterations, ignored), 10, getHeight() - 40);
            g.drawString(String.format("Pheromones = %.0f", map.totalPheromones), 10, getHeight() - 25);
            g.drawString(String.format("Min distance = %.2f (%d)", minDistance, minDistanceIteration), 10, getHeight() - 10);
        }
        
    }
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        TSP tsp = new TSP();
        tsp.setLocationRelativeTo(null);
        tsp.setVisible(true);
        tsp.initRandomData();
        tsp.runSimulation();
    }
    
    
    /** Město. */
    public static class City {
        
        public final float x;
        public final float y;
        public final String name;
        
        public ArrayList <Road> roads = new ArrayList();
        
        public City(float x, float y, String name) {
            this.x = x;
            this.y = y;
            this.name = name;
        }
        
        public static float distance(City a, City b) {
            return (float)Math.sqrt((a.x-b.x)*(a.x-b.x) + (a.y-b.y)*(a.y-b.y));
        }
        
    }
    
    /** Cesta mezi dvěma městy. */
    public static class Road {
        
        public final City a;
        public final City b;
        
        public final float distance;
        
        public double pheromone = 0;
        public double pheromonePending = 0;
        
        public Road(City a, City b) {
            this.a = a;
            this.b = b;
            
            distance = (float)Math.sqrt((a.x-b.x)*(a.x-b.x) + (a.y-b.y)*(a.y-b.y));
        }
        
        public City getOppositeCity(City x) {
            return a == x ? b : a;
        }
        
    }
    
    public static class CityMap {
        
        public ArrayList <City> cities = new ArrayList();
        public ArrayList <Road> roads = new ArrayList();
        
        public double totalPheromones = 0;
        public double totalPheromonesPending = 0;
        
        public CityMap() {}
        
        public City addCity(City city) {
            cities.add(city);
            return city;
        }
        
        public City addCity(float x, float y, String name) {
            City c = new City(x, y, name);
            cities.add(c);
            return c;
        }
        
        public void connectCities(City a, City b) {
            Road newRoad = new Road(a, b);
            a.roads.add(newRoad);
            b.roads.add(newRoad);
            roads.add(newRoad);
        }
        
        public float sumRoadDistances() {
            float sum = 0;
            for (Road r: roads) sum += r.distance;
            return sum;
        }
        
    }
    
}
