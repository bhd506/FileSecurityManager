package com.haydeproductions.project;

import com.haydeproductions.project.config.Config;
import com.haydeproductions.project.config.ConfigLoader;
import com.haydeproductions.project.scope.RuleSet;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {

        Path configPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("config/config.yaml");

        Path root = Path.of("test-data");

        Config config = ConfigLoader.load(configPath, root);

        for (RuleSet ruleSet : config.getRuleSets()){
            System.out.println("");
            System.out.println(ruleSet);
            ruleSet.applyRules();
        }
    }
}