import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
    public static void main(String[] args) throws Exception {
        String request = readFile("request.json");
        //cleaning up request
        request = request.replaceAll("\\s+", "");
        request = URLEncoder.encode(request, StandardCharsets.UTF_8.toString());
        String url = "https://mast.stsci.edu/api/v0/invoke?request="+request;
        String response = callSite(new URI(url));
        writeFile(response, "output.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonRespone = mapper.readTree(response);
        //data of the images
        JsonNode jsonData = jsonRespone.get("data");
        // fields and (sometimes wrong) datatypes
        JsonNode fields = jsonRespone.get("fields");
        //reads in the filter and formatting info
        JsonNode formatting = mapper.readTree(readFile("filter.json"));
        //loads the data
        ArrayList<String[]> data = new ArrayList<>();
        for(JsonNode dataPoint : jsonData){
            data.add(getData(dataPoint));
        }
        //filters out unnecessery data
        for (int i = 0; i < data.size(); i++) {
            if(!filterData(data.get(i),fields,formatting)){
                data.remove(i);
                i--;
            }
        }
        //formats the remaining data
        for (String[] datapoint : data) {
            datapoint = formatData(datapoint,fields,formatting);
        }
        //writes the remaining data to the file
        String outputFile = "";
        
        for (String[] rowArr : data) {
            String row = "";
            for (String rowPart : rowArr) {
                row +=rowPart + ",";
            }

            outputFile += row.substring(0, row.length()-1) + "\n";
        }
        writeFile(outputFile, "response.csv");
    }


    public static String[] getData(JsonNode pData) {
        int size = pData.size();
        String[] dataString = new String[size];
        int index = -1;
        for (JsonNode dataPoint : pData) {
            index++;
            String field = dataPoint.asText();
            //converts the date to the timestap in ms
            if (field.contains("Date")) {
                dataString[index] = field.replaceAll("\\D+", "");
            }else{
                dataString[index] = field;
            }
        }
        return dataString;
    }

    /**
     * @param data A row of the data
     * @param fields the fields from the response (for names)
     * @param formatting (the formatting & filter information)
     * @return If it is supposed to be accepted
     */
    public static boolean filterData(String[] data, JsonNode fields, JsonNode formatting) {
        boolean selected = true;
        for (int i = 0; i < data.length; i++) {
            String fieldName = fields.get(i).get("name").asText();
            JsonNode filterInfo;
            try {
                filterInfo = formatting.get(fieldName).get("filter");
            } catch (Exception e) {
                System.err.println("couln't filter" + fieldName);
                continue;
            }

            switch (filterInfo.get("filterType").asText()) {
                case "range":
                    long from = filterInfo.get("from").longValue();
                    long to = filterInfo.get("to").longValue();
                    long num = Long.parseLong(data[i]);
                    if (num < from || to < num) {
                        selected = false;
                    }
                    break;

                case "regex-allow":
                    selected = data[i].matches(filterInfo.get("filter").asText());
                    break;
                
                case "regex-deny":
                    selected = !data[i].matches(filterInfo.get("filter").asText());
                    break;
                default:
                    break;
            }

        }
        return selected;
    }

    /**Formats the data as specified in filter.json
     * 
     * @param data A row of Data
     * @param fields the names of fileType
     * @param formatting the rules for formatting
     * @return The newly formatted row of data
     */
    public static String[] formatData(String[] data, JsonNode fields, JsonNode formatting) {

        for (int i = 0; i < data.length; i++) {
            String fieldName = fields.get(i).get("name").asText();
            JsonNode filterInfo = formatting.get(fieldName).get("format");

            switch (filterInfo.get("formatType").asText()) {
                case "surround":
                    String prefix = filterInfo.get("prefix").asText();
                    String suffix = filterInfo.get("suffix").asText();
                    data[i] = prefix + data[i] + suffix;
                    break;

                case "date":
                    try {
                        String dateFormat = filterInfo.get("formatting").asText();
                        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);
                        long timestamp = Long.parseLong(data[i]);
                        data[i] = formatter.format(new Date(timestamp));
                    } catch (Exception e) {
                        System.err.println("couldn't format field as date!");
                    }
                    break;

                case "fileSize":
                    String ending = filterInfo.get("formatting").asText();
                    switch (ending) {
                        case "KB":
                            data[i] = data[i].substring(0, data[i].length()-3) + "KB";
                            break;

                        case "MB":
                            data[i] = data[i].substring(0, data[i].length()-6) + "MB";
                            break;
                        default:
                            break;
                    }
                    break;

                default:
                    break;
            }
        }
        return data;
    }
    /**
     * @param content of the created file
     * @param filename of the file(with file ending)
     */
    public static void writeFile(String content, String filename) {
        File output = new File(filename);
        try {
            output.createNewFile();
        } catch (Exception e) {
            System.err.println("Couldn't create " + filename);
            
        }
        try {
            FileWriter writer = new FileWriter(output);
            writer.write(content + "\n");
            writer.close();
        } catch (Exception e) {
            System.err.println("couldn't write to file " + filename);
        }
    }
    /** reads in the request.json file
     * @return The content of the file
     */
    public static String readFile(String fileName) {
        File request = new File(fileName);
        try {
            Scanner reader = new Scanner(request);
            String lines = "";
            while (reader.hasNextLine()) {
                String data = reader.nextLine();
                lines += data;
            }
            reader.close();
            return lines;
        } catch (FileNotFoundException e) {
            System.err.println("Input file could not be found");
        }
        return "";
    }

    /**
     * @param website URL of the website
     * @return The respone of the website
     * @throws Exception if the website couldn't be reached
     */
    public static String callSite(URI website) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(website)
            .GET()
            .build();
        
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        
        if(response.statusCode() != 200){
            System.out.println("got " + response.statusCode() + "as a Status code back instead of 200");
        }
        return response.body();
    }
}
