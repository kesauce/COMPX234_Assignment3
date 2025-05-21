import java.io.RandomAccessFile;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;

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

                // Split the response
                String[] responseArray = response.split(" ");

                // Check the status of the request
                if(responseArray[0].equals("OK")){
                    // Ensure we are at the right file
                    if (!responseArray[1].equals(file)){
                        System.out.println("Error: file name doesn't match");
                        continue;
                    }
                    
                    // Find the file size and data port
                    int fileSize = Integer.parseInt(responseArray[3]);
                    int portNumber = Integer.parseInt(responseArray[5]);

                    // Create a new random access file to write the file
                    RandomAccessFile raf = new RandomAccessFile("Client_Files/" + file, "rw");

                    // List to keep track of the received bytes and requested bytes
                    ArrayList<int[]> receivedBytes = new ArrayList<int[]>();
                    ArrayList<int[]> requestedBytes = new ArrayList<int[]>();

                    // Send to the server the request on the new port
                    for (int i = 0; i < fileSize; i += 1000) {
                        // Declare an end byte that doesn't exceed the file size
                        int endByte = Math.min(i + 999, fileSize - 1);

                        // Send request
                        String dataRequest = "FILE " + file + " GET START " + i + " END " + endByte;
                        byte[] dataBytes = dataRequest.getBytes();
                        DatagramPacket dataPacket = new DatagramPacket(dataBytes, dataBytes.length, serverAddress, portNumber);
                        clientSocket.send(dataPacket);

                        // Add the range to requested bytes
                        requestedBytes.add(new int[]{i, endByte});
                    }

                    // Keep count on how many times the client retries to request a missing packet
                    int retries = 0;

                    // Wait for responses from the server
                    while (!requestedBytes.isEmpty() && retries < 5){
                        try{
                            // Timeout after 2 seconds
                            clientSocket.setSoTimeout(2000); 

                            // Receive data from the server
                            byte[] downloadedData = new byte[2048];
                            DatagramPacket downloadedPacket = new DatagramPacket(downloadedData, downloadedData.length);
                            clientSocket.receive(downloadedPacket);
                            String dataResponse = new String(downloadedPacket.getData(), 0, downloadedPacket.getLength());
                            System.out.println("Server response: " + dataResponse);

                            // Split the get the data from the response
                            String[] dataResponseArray = dataResponse.split(" ");

                            // Grab the start and end bytes
                            int startByte = Integer.parseInt(dataResponseArray[4]);
                            int endByte = Integer.parseInt(dataResponseArray[6]);

                            // Check if the start and end bytes have already been written
                            boolean isDuplicate = false;
                            for (int[] range : receivedBytes) {
                                if (Arrays.equals(range, new int[]{startByte, endByte})){
                                    isDuplicate = true;
                                    break;
                                }
                            }

                            // If duplicate has been found then continue
                            if (isDuplicate){
                                continue;
                            }

                            // Find where the word DATA is
                            int dataIndex = dataResponse.indexOf("DATA ");

                            // Decode the data to bytes
                            String base64Data = dataResponse.substring(dataIndex + 5).trim();
                            byte[] rangeByte = Base64.getDecoder().decode(base64Data);

                            // Write to the random access file
                            raf.seek(startByte);
                            raf.write(rangeByte);

                            // Keep track of the bytes that have been written already and remove it from requested bytes
                            receivedBytes.add(new int[]{startByte, endByte});
                            int removeIndex = 0;
                            for (int[] range : requestedBytes) {
                                if (range[0] == startByte && range[1] == endByte)
                                    removeIndex = requestedBytes.indexOf(range);
                                    break;
                            }
                            requestedBytes.remove(removeIndex);
                        }
                        // If timeout occurs
                        catch (SocketTimeoutException e){
                            // Loop through the requested list and re-request them
                            for (int[] range : requestedBytes) {
                                // Send request
                                String dataRequest = "FILE " + file + " GET START " + range[0] + " END " + range[1];
                                byte[] dataBytes = dataRequest.getBytes();
                                DatagramPacket dataPacket = new DatagramPacket(dataBytes, dataBytes.length, serverAddress, portNumber);
                                clientSocket.send(dataPacket);
                            }

                            // Increment the number of retries
                            retries++;

                        }
                        
                    }

                    // Close the file
                    raf.close();
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