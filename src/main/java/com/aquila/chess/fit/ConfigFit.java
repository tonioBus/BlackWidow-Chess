package com.aquila.chess.fit;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@XmlRootElement(name = "fit")
public class ConfigFit {

    @XmlAttribute(required = true)
    private double updateLr;

    @XmlAttribute()
    private int fitChunk = 40;

    @XmlAttribute(required = true)
    private String nnReference;

    @XmlAttribute
    private boolean simulation = false;

    @XmlElement(required = true)
    private List<ConfigSet> configSets;

}
