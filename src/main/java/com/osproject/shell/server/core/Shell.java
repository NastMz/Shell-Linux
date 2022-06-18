package com.osproject.shell.server.core;

import com.osproject.shell.server.ShellServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Shell {

    public String execute(String command) {

        StringBuilder response = new StringBuilder();

        //Runtime r = Runtime.getRuntime();
        ProcessBuilder processBuilder = new ProcessBuilder().command(command.split(" "));

        try {

            //Process process = r.exec(command);
            Process process = processBuilder.start();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String data;

            while ((data = bufferedReader.readLine()) != null) {
                response.append(data).append(":///:");
            }

        } catch (IOException ex) {
            Logger.getLogger(ShellServer.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (response.isEmpty()){
            response.append("Comando no valido");
        }

        return response.toString();
    }

}
