package com.haydeproductions.project;

import com.haydeproductions.project.config.Config;
import com.haydeproductions.project.config.ConfigActionServices;
import com.haydeproductions.project.config.ConfigLoader;
import com.haydeproductions.project.log.FileLogHandler;
import com.haydeproductions.project.log.LogHandler;
import com.haydeproductions.project.quarantine.QuarantineService;
import com.haydeproductions.project.rule.action.ActionExecutor;
import com.haydeproductions.project.runtime.FileSecurityRuntime;
import com.haydeproductions.project.scan.FileScanner;
import com.haydeproductions.project.scan.RuleSetIndex;
import com.haydeproductions.project.scan.ScanCoordinator;
import com.haydeproductions.project.state.FileStateRegistry;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {

        Path configPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("config/config.yaml");

        Path root = Path.of("test-data")
                .toAbsolutePath()
                .normalize();

        LogHandler logHandler = new FileLogHandler(
                Path.of("logs/events.jsonl"),
                Path.of("logs/deletions.jsonl")
        );

        QuarantineService quarantineService =
                new QuarantineService(
                        Path.of("quarantine")
                );

        Config config = ConfigLoader.load(
                configPath,
                root,
                ConfigActionServices.of(
                        logHandler,
                        quarantineService
                )
        );

        FileStateRegistry stateRegistry =
                new FileStateRegistry();

        RuleSetIndex ruleSetIndex =
                new RuleSetIndex(
                        config.getRuleSets()
                );

        ScanCoordinator scanCoordinator =
                new ScanCoordinator(ruleSetIndex);

        ActionExecutor actionExecutor =
                new ActionExecutor(stateRegistry);

        FileScanner fileScanner =
                new FileScanner(
                        scanCoordinator,
                        actionExecutor,
                        stateRegistry
                );

        try (FileSecurityRuntime runtime =
                     new FileSecurityRuntime(
                             root,
                             fileScanner,
                             stateRegistry,
                             logHandler
                     )) {

            runtime.start();

            System.out.println(
                    "File security runtime active on: "
                            + root
            );

            Thread.currentThread().join();
        }
    }
}