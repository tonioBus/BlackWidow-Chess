package com.aquila.chess.fit;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class ConfigFitTest {

    @Test
    void testConfigTest() throws JAXBException {
        ConfigDir configDir0 = new ConfigDir(
                "train",
                0,
                100,
                null,
                null,
                null);
        ConfigDir configDir1 = new ConfigDir(
                "train-rog",
                0,
                60,
                null,
                null,
                null);
        ConfigDir configDir2 = new ConfigDir(
                "train-grospc",
                54,
                234,
                null,
                null,
                null);
        List<ConfigDir> configDirs = new ArrayList<>();
        configDirs.add(configDir0);
        configDirs.add(configDir1);
        configDirs.add(configDir2);
        ConfigSet configSet = new ConfigSet(configDirs, 0, true);
        List<ConfigSet> configSets = new ArrayList<>();
        configSets.add(configSet);
        ConfigFit configFit = new ConfigFit(
                4.5e-4,
                40,
                "../AGZ_NN/AGZ.reference",
                true,
                configSets
        );
        JAXBContext context = JAXBContext.newInstance(ConfigFit.class);
        Marshaller mar = context.createMarshaller();
        mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        mar.marshal(configFit, new File("config/configFit.template.xml"));
    }

    @Test
    void testLoad() {

    }
}