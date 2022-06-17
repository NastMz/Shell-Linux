/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.osproject.shell.server.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ksmar
 */
public class Auth {

    public String[] getCredentials() {

        String content = "";
        try {
            try ( BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("users.txt")))) {
                content = bufferedReader.readLine();
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Auth.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Auth.class.getName()).log(Level.SEVERE, null, ex);
        }
        return content.split(":");
    }

    public boolean auth(String request) {

        String[] data = request.split(":");
        String[] credentials = getCredentials();

        return data[0].equals(credentials[0]) && data[1].equals(credentials[1]);

    }

}
