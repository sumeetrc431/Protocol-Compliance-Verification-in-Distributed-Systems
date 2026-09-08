package com.example.protocol;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public interface LogReader {

    Map<String, List<ProtocolEvent>> readGroupedByOrder(String source) throws IOException;
}
