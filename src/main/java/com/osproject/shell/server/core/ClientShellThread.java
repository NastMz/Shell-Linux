package com.osproject.shell.server.core;

import com.osproject.shell.server.ShellServer;
import com.osproject.shell.server.utils.ConsoleColors;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientShellThread extends Thread {

    private final Socket socket;
    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;

    public ClientShellThread(Socket socket, DataInputStream dataInputStream, DataOutputStream dataOutputStream) {
        this.socket = socket;
        this.dataInputStream = dataInputStream;
        this.dataOutputStream = dataOutputStream;
    }

    @Override
    public void run() {
        String[] request;
        boolean exit = false;
        Shell shell = new Shell();
        InetAddress clientIp = socket.getInetAddress();
        do {
            try {
                request = dataInputStream.readUTF().split("::");

                switch (request[0]) {
                    case "0" -> {
                        System.out.println(ConsoleColors.BLUE + "server@linux~$ " + ConsoleColors.YELLOW + "Closing connection with " + ConsoleColors.WHITE + clientIp + ConsoleColors.YELLOW + "..." + ConsoleColors.RESET);
                        exit = true;
                        this.socket.close();
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
                    case "2" ->
                        dataOutputStream.writeUTF(shell.execute(request[1]));
                    case "3" ->
                        dataOutputStream.writeUTF(shell.execute("free -m"));
                    case "4" ->
                        dataOutputStream.writeUTF(shell.execute("df -h"));
                    case "5" ->
                        dataOutputStream.writeUTF(shell.execute("wk '{u=$2+$4; t=$2+$4+$5; if (NR==1){u1=u; t1=t;} else print ($2+$4-u1) * 100 / (t-t1) \"%\"; }' <(grep 'cpu ' /proc/stat) <(sleep 1;grep 'cpu ' /proc/stat)"));
                    case "6" ->
                        dataOutputStream.writeUTF(shell.execute("top -b -n 1"));
                }

            } catch (IOException ex) {
                exit = true;
                Logger.getLogger(ShellServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        } while (!exit);
        System.out.println(ConsoleColors.BLUE + "server@linux~$ " + ConsoleColors.RED + "Connection with " + ConsoleColors.WHITE + clientIp + ConsoleColors.RED + " closed" + ConsoleColors.RESET);
        try {
            this.dataOutputStream.close();
            this.dataInputStream.close();
        } catch (IOException ex) {
            Logger.getLogger(ShellServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
