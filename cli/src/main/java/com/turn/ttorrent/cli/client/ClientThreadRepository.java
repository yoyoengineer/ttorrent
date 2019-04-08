package com.turn.ttorrent.cli.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientThreadRepository {
    public static Map<String, Thread> threadMap = new ConcurrentHashMap<String, Thread>();
}
