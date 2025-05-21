import java.io.FileOutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;

public class Client {
    public static void main(String[] args) {
        // Ensure the correct number of arguments are provided
        if (args.length != 3) {
            System.out.println("Usage: java Client <hostname> <port> <filepath>");
            return;
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String filepath = args[2];

        // Create a UDP socket
        try (DatagramSocket clientSocket = new DatagramSocket()) {
            System.out.println("Client is ready to send data.");

            // Create a list to store all the files to download
            List<String> fileList = Files.readAllLines(Paths.get(filepath));

            // Send the file name to the server
            for (String file : fileList) {
                // Send the file name to the server and receive response
                String encodedString = "DOWNLOAD " + file;
                DatagramPacket packet = sendAndReceive(clientSocket, hostname, port, encodedString);
                String response = new String(packet.getData(), 0, packet.getLength());

                // Split the response
                String[] responseArray = response.split(" ");

                // Check the status of the request
                if(responseArray[0].equals("OK")){
                    // Find the file size and data port
                    int fileSize = Integer.parseInt(responseArray[3]);
                    int portNumber = Integer.parseInt(responseArray[5]);

                    // Store the received bytes 
                    ArrayList<Byte> fileBytes = new ArrayList<Byte>();

                    // Send to the server the request on the new port
                    // for (int i = 0; i < fileSize; i += 1000) {
                    //     // Declare an end byte that doesn't exceed the file size
                    //     int endByte = Math.min(i + 1000, fileSize);

                    //     // Send and receive request
                    //     String dataRequest = "FILE " + file + " GET START " + i + " END " + endByte;
                    //     DatagramPacket receivedPacket = sendAndReceive(clientSocket, hostname, portNumber, dataRequest);
                    //     String dataResponse = new String(receivedPacket.getData(), 0, receivedPacket.getLength());

                    //     // Split the get the data from the response
                    //     String[] dataResponseArray = dataResponse.split(" ");

                    //     // Decode the data to bytes
                    //     String base64Data = dataResponseArray[7].trim();
                    //     byte[] rangeByte = Base64.getDecoder().decode(base64Data);

                    //     // Accumulate all the files
                    //     for (Byte b : rangeByte) {
                    //         fileBytes.add(b);
                    //     }
                    // }

                    while (true){
                        // Counter
                        int counter = 0;
                        // Declare an end byte that doesn't exceed the file size
                        int endByte = Math.min(counter + 1000, fileSize);

                        // Send and receive request
                        String dataRequest = "FILE " + file + " GET START " + counter + " END " + endByte;
                        DatagramPacket receivedPacket = sendAndReceive(clientSocket, hostname, portNumber, dataRequest);
                        String dataResponse = new String(receivedPacket.getData(), 0, receivedPacket.getLength());

                        // Split the get the data from the response
                        String[] dataResponseArray = dataResponse.split(" ");

                        // Decode the data to bytes
                        String base64Data = dataResponseArray[7].trim();
                        byte[] rangeByte = Base64.getDecoder().decode(base64Data);

                        // Accumulate all the files
                        for (Byte b : rangeByte) {
                            fileBytes.add(b);
                        }

                        // If the fileBytes have the same length as the original
                        if (fileBytes.size() == fileSize){
                            break;
                        }
                        // Increment the counter
                        else{
                            counter += 1000;
                        }
                    }

                    // Build the file in the client folder
                    byte[] fileData = new byte[fileBytes.size()];
                    for (int i = 0; i < fileBytes.size(); i++) {
                        fileData[i] = fileBytes.get(i);
                    }

                    try (FileOutputStream fos = new FileOutputStream("Client_Files/" + file)) {
                        fos.write(fileData);
                        fos.close(); 
                    }

                    // Close the file
                    String dataRequest = "FILE " + file + " CLOSE";
                    sendAndReceive(clientSocket, hostname, portNumber, dataRequest);

                }
                else if (responseArray[0].equals("ERR")){
                    System.out.println("File not found: " + file);
                    continue;
                }

            }


        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    public static DatagramPacket sendAndReceive(DatagramSocket clientSocket, String hostname, int port, String encodedString){
        try {
            // Send data
            byte[] sendData = encodedString.getBytes();
            InetAddress serverAddress = InetAddress.getByName(hostname);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
            clientSocket.send(sendPacket);

            // Receive data
            byte[] receivedData = new byte[1024];
            DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);
            clientSocket.receive(receivedPacket);
            String response = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
            System.out.println("Server response: " + response);
            //return response;
            return receivedPacket;
            
        } catch (Exception e) {
            System.out.println("There was an error with sending and receiving data.");
            return null;
        }

    }
}