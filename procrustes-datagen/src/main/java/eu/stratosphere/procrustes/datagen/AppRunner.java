package eu.stratosphere.procrustes.datagen;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.internal.HelpScreenException;

import org.reflections.Reflections;

@SuppressWarnings("unused")
public final class AppRunner {

    private final String commandsPacakge;

    private final String cliName;

    private final String systemName;

    static {
        Reflections.log = null;
    }

    @SuppressWarnings("unused")
    public AppRunner(String commandsPackage, String cliName, String systemName) {
        this.commandsPacakge = commandsPackage;
        this.cliName = cliName;
        this.systemName = systemName;
    }


    @SuppressWarnings("unused")
    public void run(String[] args) {

        HashMap<String, DataGenerator.Command> commands = new HashMap<>();

        Reflections reflections = new Reflections(commandsPacakge);

        for (Class<? extends DataGenerator.Command> clazz : reflections.getSubTypesOf(DataGenerator.Command.class)) {
            try {
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    DataGenerator.Command command = clazz.newInstance();
                    commands.put(command.name(), command);
                }
            } catch (InstantiationException | IllegalAccessException e) {
                System.out.println(String.format("ERROR: Cannot instantiate algorithm class '%s'", clazz.getCanonicalName()));
                System.exit(1);
            }
        }

        // construct argument parser
        //@formatter:off
        ArgumentParser parser = ArgumentParsers.newArgumentParser(cliName, false)
                .defaultHelp(true)
                .description(String.format("Generate a synthetic dataset with a %s job", systemName));
        parser.addSubparsers()
                .help("generator to run")
                .dest("generator.name")
                .metavar("ALGORITHM");
        parser.addArgument("-?")
                .action(Arguments.help())
                .help("show this help message and exit")
                .setDefault(Arguments.SUPPRESS);
        //@formatter:on

        // register command arguments with the arguments parser (in order of command names)
        String[] commandKeys = commands.keySet().toArray(new String[commands.size()]);
        Arrays.sort(commandKeys);
        for (String key : commandKeys) {
            DataGenerator.Command c = commands.get(key);
            c.setup(parser.addSubparsers().addParser(c.name(), false).help(c.description()));
        }

        try {
            Namespace ns = parser.parseArgs(args);

            String algorithmName = ns.getString("generator.name");

            if (algorithmName == null) {
                parser.printHelp();
                System.exit(0);
            }

            if (!commands.containsKey(algorithmName)) {
                throw new IllegalArgumentException("Illegal algorithm name");
            }

            commands.get(algorithmName).instantiate(ns).run();

        } catch (HelpScreenException e) {
            parser.handleError(e);
            System.exit(0);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected exception:");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
