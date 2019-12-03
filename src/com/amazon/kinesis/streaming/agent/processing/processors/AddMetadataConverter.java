/*
 * Copyright 2014-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file.
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.amazon.kinesis.streaming.agent.processing.processors;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TimeZone;
import java.util.Date;
import java.util.LinkedHashMap;

import com.amazon.kinesis.streaming.agent.ByteBuffers;
import com.amazon.kinesis.streaming.agent.config.Configuration;
import com.amazon.kinesis.streaming.agent.processing.exceptions.DataConversionException;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IDataConverter;
import com.amazon.kinesis.streaming.agent.processing.interfaces.IJSONPrinter;
import com.amazon.kinesis.streaming.agent.processing.utils.ProcessingUtilsFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Build record as JSON object with a "metadata" key for arbitrary KV pairs
 *   and "message" key with the raw data
 *
 * Configuration looks like:
 *
 * { 
 *   "optionName": "ADDMETADATA",
 *   "timestamp": "true/false",
 *   "metadata": {
 *     "key": "value",
 *     "foo": {
 *       "bar": "baz"
 *     }
 *   }
 * }
 *
 * @author zacharya
 *
 */
public class AddMetadataConverter implements IDataConverter {

    private Object metadata;
    private Boolean timestamp;
		private Boolean isJsonData = false;
    private final IJSONPrinter jsonProducer;

    public AddMetadataConverter(Configuration config) {
      metadata = config.getConfigMap().get("metadata");
      timestamp = new Boolean((String) config.getConfigMap().get("timestamp"));
			String dataType = (String) config.getConfigMap().get("dataType");
      isJsonData = dataType == null ? false : dataType.equals("JSON");
      jsonProducer = ProcessingUtilsFactory.getPrinter(config);
    }

    @Override
    public ByteBuffer convert(ByteBuffer data) throws DataConversionException {

        final Map<String, Object> recordMap = new LinkedHashMap<String, Object>();
        String dataStr = ByteBuffers.toString(data, StandardCharsets.UTF_8);
	
        recordMap.put("metadata", metadata);
				recordMap.put("dataType", isJsonData?"JSON":"TEXT");
				if (isJsonData) {
					ObjectMapper mapper = new ObjectMapper();
					TypeReference<LinkedHashMap<String,Object>> typeRef = 
						      new TypeReference<LinkedHashMap<String,Object>>() {};
        	LinkedHashMap<String,Object> dataObj = null;
		    	try {
			   	   dataObj = mapper.readValue(dataStr, typeRef);
					} catch (Exception ex) {
				 	   throw new DataConversionException("Error converting json source data to map", ex);
        	}
        	recordMap.put("data", dataObj);
				} else {
        	if (dataStr.endsWith(NEW_LINE)) {
            dataStr = dataStr.substring(0, (dataStr.length() - NEW_LINE.length()));
        	}
        	recordMap.put("data", dataStr);
				}

        if (timestamp.booleanValue()) {
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(tz);
            recordMap.put("ts", dateFormat.format(new Date()));
        }


        String dataJson = jsonProducer.writeAsString(recordMap) + NEW_LINE;
        return ByteBuffer.wrap(dataJson.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
