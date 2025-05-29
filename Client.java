import java.io.RandomAccessFile;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Base64;

public class Client {
    public static void main(String[] args) {
        // Ensure the correct number of arguments are provided
        if (args.length != 3) {
            System.out.println("Usage: java Client <hostname> <port> <filepath>");
            return;
        }

        String hostname = args[0];
        int port;

        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Please provide a valid integer.");
            return;
        }
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
                sendMessage(clientSocket, hostname, port, encodedString);

                // Receive data
                String response = receiveMessage(clientSocket);
                System.out.println("Server response: " + response);

                // Split the response
                String[] responseArray = response.split(" ");

                // Check the status of the request
                if(responseArray[0].equals("OK") && responseArray.length == 6){
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

                    // Send to the server the request on the new port
                    for (int i = 0; i < fileSize; i += 1000) {

                        // Set amount of retries 
                        int retries = 0;
                        while (retries < 5){
                            try {
                                // Declare an end byte that doesn't exceed the file size
                                int end = Math.min(i + 999, fileSize - 1);

                                // Send request
                                String dataRequest = "FILE " + file + " GET START " + i + " END " + end;

                                sendMessage(clientSocket, hostname, portNumber, dataRequest);

                                // Timeout after 2 seconds
                                clientSocket.setSoTimeout(2000); 

                                // Receive data from the server (can't use the custom method to handle timeout exception properly)
                                byte[] downloadedData = new byte[2048];
                                DatagramPacket downloadedPacket = new DatagramPacket(downloadedData, downloadedData.length);
                                clientSocket.receive(downloadedPacket);
                                String dataResponse = new String(downloadedPacket.getData(), 0, downloadedPacket.getLength());
                                System.out.println("Server response: " + dataResponse);

                                // Split the get the data from the response
                                String[] dataResponseArray = dataResponse.split(" ");

                                // If server response is invalid 
                                if (dataResponseArray.length != 9){
                                    System.out.println("Error: invalid server response");
                                    continue;
                                }

                                // Grab the start bytes
                                int startByte = Integer.parseInt(dataResponseArray[4]);

                                // Find where the word DATA is
                                int dataIndex = dataResponse.indexOf("DATA ");

                                // Decode the data to bytes
                                String base64Data = dataResponse.substring(dataIndex + 5).trim();
                                byte[] rangeByte = Base64.getDecoder().decode(base64Data);

                                // Write to the random access file
                                raf.seek(startByte);
                                raf.write(rangeByte);

                                break;
                                
                            } catch (SocketTimeoutException e) {
                                System.out.println("Error: timeout occured. Retrying.");
                                retries++;
                            }
                        }
                        
                    }

                    // Close the file
                    raf.close();
                    String dataRequest = "FILE " + file + " CLOSE";
                    sendMessage(clientSocket, hostname, portNumber, dataRequest);
                    String closeMessage = receiveMessage(clientSocket);
                    System.out.println("Server response: " + closeMessage);
                    continue;

                }

                // File not found
                else if (responseArray[0].equals("ERR") && responseArray.length == 3){
                    System.out.println("File not found: " + file);
                    continue;
                }

                // Invalid response
                else{
                    System.out.println("Error: invalid server response");
                    continue;
                }

            }


        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }

    }

    private static void sendMessage(DatagramSocket clientSocket, String hostname, int port, String encodedString){
        try {
            // Send data
            byte[] sendData = encodedString.getBytes();
            InetAddress serverAddress = InetAddress.getByName(hostname);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
            clientSocket.send(sendPacket);
            
        } catch (Exception e) {
            System.out.println("There was an error with sending and receiving data.");
        }

    }

    private static String receiveMessage(DatagramSocket socket){
        try {
            byte[] downloadedData = new byte[2048];
            DatagramPacket downloadedPacket = new DatagramPacket(downloadedData, downloadedData.length);
            socket.receive(downloadedPacket);
            String dataResponse = new String(downloadedPacket.getData(), 0, downloadedPacket.getLength());
            return dataResponse;
        } 
        catch (Exception e) {
            System.out.println("Error receiving message");
            return null;
        }
    }
}