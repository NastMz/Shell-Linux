package com.osproject.shell.server.core;

import com.osproject.shell.server.ShellServer;
import com.osproject.shell.server.utils.ConsoleColors;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientShellThread extends Thread{

    private final Socket socket;
    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;

    public ClientShellThread(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream){
        this.socket = socket;
        this.dataInputStream = dataInputStream;
        this.dataOutputStream = dataOutputStream;
    }

    @Override
    public void run() {
        String[] request;
        boolean exit = false;
        while(!exit) {
            try {
                request = dataInputStream.readUTF().split("::");

                switch (request[0]) {
                    case "0" -> {
                        exit = true;
                        this.socket.close();
                        System.out.println(ConsoleColors.BLUE + "server@linux~$ " + ConsoleColors.YELLOW + "Closing connection with " + ConsoleColors.WHITE + socket.getInetAddress() + ConsoleColors.YELLOW + "..." + ConsoleColors.RESET);
                        System.out.println(ConsoleColors.BLUE + "server@linux~$ " + ConsoleColors.RED + "Connection closed" + ConsoleColors.RESET);
                    }
                    case "1" -> {
                        boolean login = new Auth().auth(request[1]);
                        dataOutputStream.writeBoolean(login);
                        if (!login) {
                            System.out.println(ConsoleColors.BLUE + "server@linux~$ " + ConsoleColors.RED + "Connection request from " + ConsoleColors.WHITE + socket.getInetAddress() + ConsoleColors.RED + " denied" + ConsoleColors.RESET);
                        } else {
                            System.out.println(ConsoleColors.BLUE + "server@linux~$ " + ConsoleColors.GREEN + "Connection request from " + ConsoleColors.WHITE + socket.getInetAddress() + ConsoleColors.GREEN + " accepted" + ConsoleColors.RESET);
                        }
                    }
                    case "2" -> dataOutputStream.writeUTF(new Shell().shell(request[1]));
                    case "3" -> dataOutputStream.writeUTF("");
                }

            } catch (IOException ex) {
                Logger.getLogger(ShellServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        try {
            this.dataOutputStream.close();
            this.dataInputStream.close();
        } catch (IOException ex) {
            Logger.getLogger(ShellServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
