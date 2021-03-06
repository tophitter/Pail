/*
 * Copyright (c) 2010 SimpleServer authors (see CONTRIBUTORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.dumptruckman.pail.proxyserver;

import com.dumptruckman.pail.Pail;
import com.dumptruckman.pail.config.ServerProperties;
import com.dumptruckman.pail.listmodel.GUIListModel;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 *
 * @author dumptruckman
 */
public class ProxyServer {

    public ProxyServer(Pail pail, ServerProperties serverProps) {
        listener = new Listener();
        run = true;
        startCode = 0;
        //this.addObserver(pail.server);
        listener.start();
        this.pail = pail;
        this.serverProps = serverProps;
    }

    public void stop() {
        try {
            socket.close();
        } catch (Exception e) {
        }
        run = false;
        listener.interrupt();
    }

    public int getStartCode() {
        try {
            synchronized (listener) {
                listener.wait();
            }
        } catch (InterruptedException ie) {
            
        }
        return startCode;
    }

    public void kick(String name, String reason) {
        Player player = playerList.findPlayer(name);
        if (player != null) {
            player.kick(reason);
        }
    }

    public void banKick(String name, String msg) {
        if (name != null) {
            pail.sendInput("ban " + name);
            Player p = playerList.findPlayer(name);
            if (p != null) {
                //adminLog("Player " + p.getName() + " was banned:\t " + msg);
                p.kick(msg);
            }
        }
    }

    public String findName(String prefix) {
        Player i = playerList.findPlayer(prefix);
        if (i != null) {
            return i.getName();
        }

        return null;
    }

    public Player findPlayer(String prefix) {
        return playerList.findPlayer(prefix);
    }

    public Player findPlayerExact(String exact) {
        return playerList.findPlayerExact(exact);
    }

    private void kickAllPlayers(String message) {
        for (Player player : playerList.getArray()) {
            player.kick(message);
        }
    }

    private final class Listener extends Thread {
        @Override public void run() {
            //initialize();

            while (run) {
                //startup();
                playerList = new PlayerList();
                pail.setPlayerList(playerList);

                //String ip = options.get("ipAddress");

                String ip = "0.0.0.0";
                if (ip.isEmpty()) {
                    ip = "0.0.0.0";
                }
                int port = pail.config.getExtPort();

                synchronized (this) {
                    InetAddress address;
                    if (pail.config.getExtPort().equals(pail.config.getIntPort())) {
                        pail.guiLog("The Proxy's internal and external port are set to the same thing.  " +
                                "Please correct this in the Configuration Tab.", Pail.LogLevel.SEVERE);
                        startCode = -1;
                        this.notifyAll();
                        break;
                    }
                    if (serverProps.getServerPort().equals(pail.config.getExtPort().toString())) {
                        pail.guiLog("The Proxy External Port cannot be the same as the Minecraft Server Port!  " +
                                "Please change this in the Configuration Tab!", Pail.LogLevel.SEVERE);
                        startCode = -1;
                        this.notifyAll();
                        break;
                    }
                    if (ip.equals("0.0.0.0")) {
                        address = null;
                    } else {
                        try {
                            address = InetAddress.getByName(ip);
                        } catch (UnknownHostException e) {
                            pail.guiLog(e.toString(), Pail.LogLevel.SEVERE);
                            pail.guiLog("Invalid listening address " + ip + "!"
                                    + "<br>Aborting...", Pail.LogLevel.SEVERE);
                            startCode = -1;
                            this.notifyAll();
                            break;
                        }
                    }

                    try {
                        socket = new ServerSocket(port, 0, address);
                    } catch (java.io.IOException e) {
                        pail.guiLog(e.toString(), Pail.LogLevel.SEVERE);
                        pail.guiLog("Could not listen on port " + port + "!<br>"
                                + "Is it already in use? Aborting...",
                                Pail.LogLevel.SEVERE);
                        startCode = -1;
                        this.notifyAll();
                        break;
                    }

                    pail.guiLog("Forwarding network traffic on port " + port + " to port "
                            + pail.config.getIntPort() + ".  Players should connect to port " + port);
                    if (!serverProps.getServerPort().equals(pail.config.getIntPort().toString())) {
                        pail.guiLog("The proxy's Internal Port is not set to the same as your Minecraft Server's port." +
                                "Typically they should be the same unless you're using an additional proxy.");
                    }
                    startCode = 1;
                    this.notifyAll();
                }

                try {
                    while (true) {
                        Socket client;
                        try {
                            client = socket.accept();
                        } catch (java.io.IOException e) {
                            pail.guiLog("Proxy server stopped.");
                            //pail.guiLog(e.toString(), Pail.LogLevel.WARNING);
                            //pail.guiLog("Accept failed on port " + port + "!"
                            //        + "  Server likely stopped.",
                            //        Pail.LogLevel.WARNING);
                            break;
                        }
                        new Player(client, ProxyServer.this);
                    }
                } finally {
                    try {
                        socket.close();
                    } catch (java.io.IOException e) {
                    }
                }

                //ProxyServer.this.stop();
            }
            ProxyServer.this.stop();
            if (pail.server.isRunning()) {
                pail.stopServer();
            }
        }
    }

    private int startCode;
    private Listener listener;
    private ServerSocket socket;
    public PlayerList playerList;
    public ServerProperties serverProps;
    public Pail pail;
    //private Config config;
    private boolean run;
}
