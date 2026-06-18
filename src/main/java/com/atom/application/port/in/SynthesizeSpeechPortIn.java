package com.atom.application.port.in;

import java.util.stream.Stream;

/** Streams audio chunks (WAV bytes) synthesized by the backend voice engine. */
public interface SynthesizeSpeechPortIn {
    Stream<byte[]> synthesize(String text, String voice, String language, String format, float speed);
}
