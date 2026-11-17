package com.dtos.pdfs;

public record FrequencyDto(String name, int presences, int absents, int justified) {

    public int totalClasses() {
        return presences + absents + justified;
    }

    public double percent() {
        if (totalClasses() == 0) {
            return 0;
        }

        return presences * 100.0 / totalClasses();
    }
}
