package custom;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConvertClusterDataToSequenceData {

    private static final String ARFF_PATH = "custom/taxidata/100taxi-month1/100taxi-month1-weka.arff";
    private static final String TAXI_PATH = "custom/taxidata/100taxi-month1/100taxi-month1-training/";

    public static void main(String[] args) throws IOException {
        List<List<String>> splittedLines = read();
        StringBuilder stringBuilder = new StringBuilder();
        splittedLines.forEach(line -> {
            List<List<String>> partitionedLines = new ArrayList<>();
            for (int i = 0; i < line.size() - 4; i++) {
                partitionedLines.add(line.subList(i, i + 5));
            }
            partitionedLines.forEach(partitioned -> {
                stringBuilder.append(partitioned.stream().collect(Collectors.joining(" -1 "))).append(" -2");
                stringBuilder.append(System.lineSeparator());
            });
        });

        writeToFile(stringBuilder.toString());
    }

    public static List<List<String>> read() throws IOException {
        List<String> allLines = Files.readAllLines(Paths.get(ArffReader.class.getClassLoader().getResource(ARFF_PATH).getPath()));
        ForkJoinPool forkJoinPool = new ForkJoinPool(60);
        //List<ArffRegion> arffRegions = new ArrayList<>();
        Map<String, ArffRegion> arffRegions = new HashMap<>();
        forkJoinPool.submit(() -> allLines.forEach(line -> {
            String[] regionString = line.split(",");
            if (regionString.length == 4) {
                ArffRegion arffRegion = new ArffRegion(Double.parseDouble(regionString[1]), Double.parseDouble(regionString[2]), regionString[3]);
                //arffRegions.add(arffRegion);
                arffRegions.put(arffRegion.getxPoint() + "#" + arffRegion.getyPoint(), arffRegion);
            }
        })).join();

        return getRegionListForAllFiles(arffRegions);
    }

    private static List<List<String>> getRegionListForAllFiles(Map<String, ArffRegion> arffRegions) throws IOException {
        String rootFolder = ArffReader.class.getClassLoader().getResource(TAXI_PATH).getPath();
        List<String> files = Stream.of(new File(rootFolder).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toList());

        List<List<String>> distinctedRegions = new ArrayList<>();

        ForkJoinPool forkJoinPool = new ForkJoinPool(files.size());
        forkJoinPool.submit(() -> files.parallelStream().forEach(file -> {
            InputStream stream = ArffReader.class.getClassLoader().getResourceAsStream(TAXI_PATH + file);
            System.out.println("file " + file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line = null;
            try {
                line = reader.readLine();
                String lineStringLine = "";
                while (line != null) {
                    lineStringLine = line;
                    line = reader.readLine();
                }
                reader.close();
                LineStringReader lineStringReader = new LineStringReader(lineStringLine);
                lineStringReader.parse();

                /*List<String> region = lineStringReader.getLandmarks()
                        .stream()
                        .map(landmark -> getRegionByPoints(arffRegions, landmark.getX(), landmark.getY())).collect(Collectors.toList());*/
                List<String> region = new ArrayList<>();
                for (Landmark landmark : lineStringReader.getLandmarks()) {
                    region.add(getRegionByPoints(arffRegions, landmark.getX(), landmark.getY()));
                }

                distinctedRegions.add(getDistinctRegions(region));
            } catch (IOException e) {
                e.printStackTrace();
            }

        })).join();
        return distinctedRegions;

    }

    private static List<String> getDistinctRegions(List<String> regions) {
        final String[] lookupRegion = {""};
        List<String> updatedTaxiPath = new ArrayList<>();
        regions.forEach(region -> {
            if (!region.equals(lookupRegion[0]) && !region.equals("")) {
                lookupRegion[0] = region;
                updatedTaxiPath.add(region);
            }
        });
        return updatedTaxiPath;
    }

    private static String getRegionByPoints(Map<String, ArffRegion> arffRegions, Double xPoint, Double yPoint) {
        /*return arffRegions
                .parallelStream()
                .filter(arffRegion -> arffRegion.getxPoint().equals(xPoint) && arffRegion.getyPoint().equals(yPoint))
                .findAny()
                .orElse(new ArffRegion(0.0, 0.0, ""))
                .getRegion()
                .replace("cluster", "");*/
        ArffRegion region = arffRegions.get(xPoint + "#" + yPoint);
        if (region != null) {
            return region.getRegion().replace("cluster", "");
        } else {
            return "";
        }
    }

    private static void writeToFile(String content) throws IOException {
        Files.write(Paths.get(ConvertClusterDataToSequenceData.class.getClassLoader().getResource("training-data100.txt").getPath()),
                content.getBytes(), StandardOpenOption.WRITE);
        System.out.println("finished successfully.");
    }
}
