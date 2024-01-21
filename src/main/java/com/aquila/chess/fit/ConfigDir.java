package com.aquila.chess.fit;

import jakarta.xml.bind.annotation.XmlAttribute;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ConfigDir {
    @XmlAttribute(required = true)
    private String directory;

    @XmlAttribute
    private int startNumber;

    @XmlAttribute
    private int endNumber;

    @XmlAttribute
    private Date startDate;

    @XmlAttribute
    private Date endDate;

    @XmlAttribute
    private String excludeGamesValue;
}
