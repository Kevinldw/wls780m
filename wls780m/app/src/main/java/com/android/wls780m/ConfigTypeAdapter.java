package com.android.wls780m;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class ConfigTypeAdapter
  extends TypeAdapter<Config>
{
  public Config read(JsonReader paramJsonReader)
    throws IOException
  {
    return null;
  }
  
  public void write(JsonWriter paramJsonWriter, Config config)
    throws IOException
  {
    paramJsonWriter.beginObject();
    paramJsonWriter.name("cur_sensor").value(config.cur_sensor);
    paramJsonWriter.name("cur_channel").value(config.cur_channel);
    paramJsonWriter.name("cur_pan").value(config.cur_pan);
    paramJsonWriter.endObject();
  }
}
