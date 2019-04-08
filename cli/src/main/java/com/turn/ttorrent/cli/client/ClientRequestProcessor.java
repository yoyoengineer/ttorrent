package com.turn.ttorrent.cli.client;

import com.turn.ttorrent.cli.ClientMain;
import com.turn.ttorrent.client.SimpleClient;
import com.turn.ttorrent.common.TorrentMetadata;
import com.turn.ttorrent.common.TorrentParser;
import org.simpleframework.http.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ClientRequestProcessor {

    private static final Logger logger =
            LoggerFactory.getLogger(ClientRequestProcessor.class);

    public void process(final String content, RequestHandler requestHandler, CMDType cmdType) {
        String[] contents = content.split("\r\n");
        Map<String, String> params = new HashMap<String, String>();
        for (int i = 0; i < contents.length - 1; i = i + 4) {
            String key = contents[i + 1].split(";")[1].split("\"")[1];
            String value = contents[i + 3];
            params.put(key, value);
        }
        requestHandler.serveResponse(Status.OK.getCode(), Status.OK.getDescription(), ByteBuffer.wrap(Status.OK.getDescription().getBytes()));
        switch (cmdType) {
            case START:
                start(params);
                break;
            case STOP:
                stop(params);
                break;
        }

    }

    private void stop(Map<String, String> params) {
        String hexInfoHash = params.get(ParamKeys.HEX_INFO_HASH);
        if (!hexInfoHash.isEmpty()) {
            Thread threadToBeStopped = ClientThreadRepository.threadMap.get(hexInfoHash.toUpperCase());
            if (threadToBeStopped != null) {
                threadToBeStopped.interrupt();
                ClientThreadRepository.threadMap.remove(hexInfoHash);
            }
        }
    }

    private void start(Map<String, String> params) {
        String torrentPath = params.get(ParamKeys.TORRENT_PATH);
        String metainfoPath = params.get(ParamKeys.METAINFO_PATH);
        String seedTime = params.get(ParamKeys.SEED);
        String domainName = params.get(ParamKeys.DOMAIN_NAME);
        int seedTimeValue = -1;
        if (seedTime != null && !seedTime.isEmpty()) {
            seedTimeValue = Integer.valueOf(seedTime);
        }
        SimpleClient client = new SimpleClient();
        try {
            Inet4Address iPv4Address = ClientMain.getInet4Address(domainName);
            File metainfoFile = new File(metainfoPath);

            TorrentMetadata torrentMetadata = new TorrentParser().parseFromFile(metainfoFile);

            String directoryName = "";

            if (torrentMetadata != null) {
                directoryName = torrentMetadata.getDirectoryName();

                if (!directoryName.isEmpty() && !torrentPath.isEmpty()) {
                    torrentPath = torrentPath.concat(File.separator + directoryName);
                }

                File outputFile = new File(torrentPath);
                if (!outputFile.exists()) {
                    outputFile.mkdirs();
                }

                client.downloadTorrent(
                        metainfoFile.getAbsolutePath(),
                        outputFile.getAbsolutePath(),
                        iPv4Address);
                if (seedTimeValue > 0) {
                    String hexInfoHash = torrentMetadata.getHexInfoHash();
                    if (hexInfoHash != null) {
                        ClientThreadRepository.threadMap.put(hexInfoHash.toUpperCase(), Thread.currentThread());
                        try {
                            Thread.sleep(seedTimeValue * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fatal error: {}", e.getMessage(), e);
            System.exit(2);
        } finally {
            client.stop();
        }
    }

    public interface RequestHandler {
        void serveResponse(int code, String description, ByteBuffer responseData);
    }
}
