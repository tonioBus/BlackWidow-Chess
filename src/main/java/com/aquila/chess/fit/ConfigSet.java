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
@XmlRootElement(name = "set")
public class ConfigSet implements Comparable<ConfigSet> {

    @XmlElement(required = true)
    private List<ConfigDir> configDirs;

    @XmlAttribute(required = true)
    private int sequence;

    @XmlAttribute(required = false)
    private int chunkSize = 10;

    @XmlAttribute(required = true)
    private boolean enable;

    @Override
    public int compareTo(ConfigSet configSet) {
        return this.sequence - configSet.sequence;
    }
}
