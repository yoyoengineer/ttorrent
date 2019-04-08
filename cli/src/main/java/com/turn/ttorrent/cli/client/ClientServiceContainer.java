package com.turn.ttorrent.cli.client;

import com.turn.ttorrent.common.LoggerUtils;
import com.turn.ttorrent.common.TorrentLoggerFactory;
import org.apache.commons.io.IOUtils;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

public class ClientServiceContainer implements Container {

    private static final Logger logger =
            TorrentLoggerFactory.getLogger(ClientServiceContainer.class);

    ClientRequestProcessor clientRequestProcessor = new ClientRequestProcessor();

    @Override
    public void handle(Request req, Response resp) {
        if (!req.getPath().toString().startsWith(ClientPath.BASE_URL)) {
            resp.setCode(404);
            resp.setText("Not Found");
            return;
        }

        OutputStream body = null;
        try {
            body = resp.getOutputStream();

            resp.set("Content-Type", "text/plain");
            resp.set("Server", "");
            resp.setDate("Date", System.currentTimeMillis());

            if (HTTPMethod.POST.equalsIgnoreCase(req.getMethod()) && req.getPath().toString().equals(ClientPath.BASE_URL.concat(ClientPath.START))) {
                clientRequestProcessor.process(req.getContent(),
                        getRequestHandler(resp), CMDType.START);
            } else if (HTTPMethod.POST.equalsIgnoreCase(req.getMethod()) && req.getPath().toString().equals(ClientPath.BASE_URL.concat(ClientPath.STOP))) {
                clientRequestProcessor.process(req.getContent(),
                        getRequestHandler(resp), CMDType.STOP);
            }
            body.flush();
        } catch (IOException ioe) {
            logger.info("Error while writing response: {}!", ioe.getMessage());
        } catch (Throwable t) {
            LoggerUtils.errorAndDebugDetails(logger, "error in processing request {}", req, t);
        } finally {
            IOUtils.closeQuietly(body);
        }
    }

    private ClientRequestProcessor.RequestHandler getRequestHandler(final Response response) {
        return new ClientRequestProcessor.RequestHandler() {
            @Override
            public void serveResponse(int code, String description, ByteBuffer responseData) {
                response.setCode(code);
                response.setText(description);
                try {
                    if (responseData != null) {
                        responseData.rewind();
                    }
                    final WritableByteChannel channel = Channels.newChannel(response.getOutputStream());
                    channel.write(responseData);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
