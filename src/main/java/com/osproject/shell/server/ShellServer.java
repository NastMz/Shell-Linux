/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package com.osproject.shell.server;

import com.osproject.shell.server.core.Auth;
import com.osproject.shell.server.utils.ConsoleColors;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ksmar
 */
public class ShellServer {

    public static void main(String[] args) {

        ServerSocket serverSocket = null;
        try {
            System.out.println(ConsoleColors.BLUE + "server@linux~$" + ConsoleColors.CYAN + "Creating Socket on port 2704..." + ConsoleColors.RESET);
            serverSocket = new ServerSocket(2704);
        } catch (IOException ex) {
            Logger.getLogger(ShellServer.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println(ConsoleColors.BLUE + "server@linux~$" + ConsoleColors.RED + "Socket creation failed" + ConsoleColors.RESET);
        }
        Socket socket;

        DataInputStream dataInputStream;
        DataOutputStream dataOutputStream;
        String request;

        System.out.println(ConsoleColors.BLUE + "server@linux~$" + ConsoleColors.GREEN + "Socket creation success" + ConsoleColors.RESET);

        while (true) {
            try {

                boolean login = false;

                System.out.println(ConsoleColors.BLUE + "server@linux~$" + ConsoleColors.CYAN + "Waiting for connections..." + ConsoleColors.RESET);

                //Espero la conexion del cliente
                socket = serverSocket.accept();

                dataInputStream = new DataInputStream(socket.getInputStream());
                dataOutputStream = new DataOutputStream(socket.getOutputStream());

                while (!login) {

                    System.out.println(ConsoleColors.BLUE + "server@linux~$" + ConsoleColors.YELLOW + "New connection request from " + ConsoleColors.WHITE + socket.getInetAddress() + ConsoleColors.RESET);

                    request = dataInputStream.readUTF();

                    login = new Auth().auth(request);

                    dataOutputStream.writeBoolean(login);

                    if (!login) {
                        System.out.println(ConsoleColors.BLUE + "server@linux~$" + ConsoleColors.RED + "Connection request from" + ConsoleColors.WHITE + socket.getInetAddress() + "denied" + ConsoleColors.RESET);
                        socket.close();
                        socket = serverSocket.accept();
                    }

                }

                socket.close();

                if (login) {

                    //ServidorHilo hilo = new ServidorHilo(fEntrada, fSalida);
                    //hilo.start();
                    System.out.println(ConsoleColors.BLUE + "server@linux~$" + ConsoleColors.GREEN + "Connection request from" + ConsoleColors.WHITE + socket.getInetAddress() + " accepted" + ConsoleColors.RESET);

                }

            } catch (IOException ex) {
                Logger.getLogger(ShellServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}
