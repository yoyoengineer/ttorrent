/**
 * Copyright (C) 2011-2013 Turn, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.turn.ttorrent.cli;

import com.turn.ttorrent.cli.client.ClientPath;
import com.turn.ttorrent.cli.client.ClientServiceContainer;
import com.turn.ttorrent.cli.client.HTTPMethod;
import com.turn.ttorrent.cli.client.ParamKeys;
import com.turn.ttorrent.client.CommunicationManager;
import jargs.gnu.CmdLineParser;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Command-line entry-point for starting a {@link CommunicationManager}
 */
public class ClientMain {

  private static final Logger logger =
          LoggerFactory.getLogger(ClientMain.class);
  private static SocketAddress myBoundAddress = null;
  private static Connection connection;
  private static final ClientServiceContainer clientServiceContainer = new ClientServiceContainer();
  private static int DEFAULT_CLIENT_PORT = 9696;

  /**
   * Default data output directory.
   */
  private static final String DEFAULT_OUTPUT_DIRECTORY = "/tmp";

  /**
   * Returns a usable {@link Inet4Address} for the given interface name.
   *
   * <p>
   * If an interface name is given, return the first usable IPv4 address for
   * that interface. If no interface name is given or if that interface
   * doesn't have an IPv4 address, return's localhost address (if IPv4).
   * </p>
   *
   * <p>
   * It is understood this makes the client IPv4 only, but it is important to
   * remember that most BitTorrent extensions (like compact peer lists from
   * trackers and UDP tracker support) are IPv4-only anyway.
   * </p>
   *
   * @param iface The network interface name.
   * @return A usable IPv4 address as a {@link Inet4Address}.
   * @throws UnsupportedAddressTypeException If no IPv4 address was available
   *                                         to bind on.
   */
  private static Inet4Address getIPv4Address(String iface)
          throws SocketException, UnsupportedAddressTypeException,
          UnknownHostException {
    if (iface != null) {
      Enumeration<InetAddress> addresses =
              NetworkInterface.getByName(iface).getInetAddresses();
      while (addresses.hasMoreElements()) {
        InetAddress addr = addresses.nextElement();
        if (addr instanceof Inet4Address) {
          return (Inet4Address) addr;
        }
      }
    }

    InetAddress localhost = InetAddress.getLocalHost();
    if (localhost instanceof Inet4Address) {
      return (Inet4Address) localhost;
    }

    throw new UnsupportedAddressTypeException();
  }

  public static Inet4Address getInet4Address(String domainName)
          throws UnsupportedAddressTypeException,
          UnknownHostException {
    if (domainName != null) {
      InetAddress address = InetAddress.getByName(domainName);
      if (address instanceof Inet4Address) {
        return (Inet4Address) address;
      }
    }

    InetAddress localhost = InetAddress.getLocalHost();
    if (localhost instanceof Inet4Address) {
      return (Inet4Address) localhost;
    }

    throw new UnsupportedAddressTypeException();
  }

  /**
   * Display program usage on the given {@link PrintStream}.
   */
  private static void usage(PrintStream s) {
    s.println("usage: Client [options]");
    s.println();
    s.println("Available options:");
    s.println("  -h,--help             Show this help and exit.");
    s.println("  -p,--port PORT        Bind to port PORT.");
    s.println();
    s.println("API:");
    s.println("start seeding");
    s.println("request address:\t" + ClientPath.BASE_URL + ClientPath.START);
    s.println("HTTP Method:\t" + HTTPMethod.POST);
    s.println("request parameters:");
    s.println("name\trequired\tdescription");
    s.println(ParamKeys.TORRENT_PATH + "\t" + true + "\t" + "The path that stored the torrents");
    s.println(ParamKeys.METAINFO_PATH + "\t" + true + "\t" + "The path that stored the metainfo files");
    s.println(ParamKeys.SEED + "\t" + false + "\t" + "The duration of the seeding.");
    s.println(ParamKeys.DOMAIN_NAME + "\t" + false + "\t" + "The domain name that corresponding to the ip of the host, which are going to start seeding.");
    s.println();
    s.println("stop seeding");
    s.println("request address:\t" + ClientPath.BASE_URL + ClientPath.STOP);
    s.println("HTTP Method:\t" + HTTPMethod.POST);
    s.println("request parameters:");
    s.println("name\trequired\tdescription");
    s.println(ParamKeys.HEX_INFO_HASH + "\t" + true + "\t" + "the hex infohash of the metainfo file");
  }

  /**
   * Main client entry point for stand-alone operation.
   */
  public static void main(String[] args) throws IOException {
    BasicConfigurator.configure(new ConsoleAppender(
            new PatternLayout("%d [%-25t] %-5p: %m%n")));

    CmdLineParser parser = new CmdLineParser();
    CmdLineParser.Option help = parser.addBooleanOption('h', "help");
    CmdLineParser.Option port = parser.addIntegerOption('p', "port");

    try {
      parser.parse(args);
    } catch (CmdLineParser.OptionException oe) {
      System.err.println(oe.getMessage());
      usage(System.err);
      System.exit(1);
    }

    // Display help and exit if requested
    if (Boolean.TRUE.equals((Boolean) parser.getOptionValue(help))) {
      usage(System.out);
      System.exit(0);
    }

    final Integer portValue = (Integer) parser.getOptionValue(port,
            DEFAULT_CLIENT_PORT);


    connection = new SocketConnection(new ContainerServer(clientServiceContainer));

    List<SocketAddress> tries = new ArrayList<SocketAddress>() {{
      try {
        add(new InetSocketAddress(InetAddress.getByAddress(new byte[4]), portValue));
      } catch (Exception ex) {
      }
      try {
        add(new InetSocketAddress(InetAddress.getLocalHost(), portValue));
      } catch (Exception ex) {
      }
    }};

    boolean started = false;
    for (SocketAddress address : tries) {
      try {
        if ((myBoundAddress = connection.connect(address)) != null) {
          logger.info("Started client on {}", address);
          started = true;
          break;
        }
      } catch (IOException ioe) {
        logger.info("Can't start the client using address{} : ", address.toString(), ioe.getMessage());
      }
    }
    if (!started) {
      logger.error("Cannot start client on port {}. Stopping now...", portValue);
      stop();
      return;
    }
  }

  public static void stop() {
    try {
      connection.close();
      logger.info("BitTorrent client closed.");
    } catch (IOException ioe) {
      logger.error("Could not stop the client: {}!", ioe.getMessage());
    }
  }
}