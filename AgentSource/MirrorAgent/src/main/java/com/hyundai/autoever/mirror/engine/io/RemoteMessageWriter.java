package com.hyundai.autoever.mirror.engine.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import com.hyundai.autoever.mirror.engine.CommandServer;
import com.hyundai.autoever.mirror.engine.MirrorEngine;
import com.hyundai.autoever.utils.AnsiLog;

public class RemoteMessageWriter implements RemoteMessageWritable {
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private OutputStream out;
    private CommandServer.ConnectionCommandWrap wrap;
    private Object lock;

    public RemoteMessageWriter(CommandServer.ConnectionCommandWrap wrap, OutputStream out) {
    	this.wrap = wrap;
        this.lock = wrap.getLockData();
        this.out = out;
    }

	@Override
	public void write(JSONObject message) {
		if(message == null)
			return;
		
		AnsiLog.d("response: " + message.toString());
		
		executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                	BufferedOutputStream output = new BufferedOutputStream(out);
                    byte[] result = message.toString().getBytes(Charset.forName("UTF-8"));
                    byte[] byteSize = MirrorEngine.toLittleEndianArray(result.length);
                    synchronized (lock) {
                    	output.write(byteSize);
                    	output.write(result);
                        output.flush();
                    }
                }
                catch (IOException e) {
                    // The socket went away
                    executor.shutdownNow();
                }
            }
        });
	}
	
	public static class Pool implements RemoteMessageWritable {
        Set<RemoteMessageWriter> writers = Collections.synchronizedSet(new HashSet<RemoteMessageWriter>());

        public void add(RemoteMessageWriter writer) {
            writers.add(writer);
        }

        public void remove(RemoteMessageWriter writer) {
            writers.remove(writer);
        }

        public void write(final JSONObject message) {
            for (RemoteMessageWriter writer : writers) {
                writer.write(message);
            }
        }
    }

}
