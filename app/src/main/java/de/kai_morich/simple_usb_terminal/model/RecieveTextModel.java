package de.kai_morich.simple_usb_terminal.model;

public class RecieveTextModel {

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String name;

    public RecieveTextModel(String name) {
        this.name = name;
    }
}
