package com.atom.application.usecase;

import com.atom.application.port.in.SynthesizeSpeechPortIn;
import com.atom.application.port.out.ExternalInteractionPortOut;

import java.util.stream.Stream;

public class SynthesizeSpeechUseCase implements SynthesizeSpeechPortIn {

    private final ExternalInteractionPortOut out;

    public SynthesizeSpeechUseCase(ExternalInteractionPortOut out) {
        this.out = out;
    }

    @Override
    public Stream<byte[]> synthesize(String text, String voice, String language, String format, float speed) {
        return out.synthesizeSpeech(text, voice, language, format, speed);
    }
}
