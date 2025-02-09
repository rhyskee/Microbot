package net.runelite.client.plugins.microbot.crafting.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Amethyst {
    NONE(" ", "", '0', 0),
    PROGRESSIVE("Progressive Mode","None", '0', 1),
    BOLT_TIPS("Amethyst bolt tips", "Amethyst bolt tips", '1', 1),
    ARROW_TIPS("Amethyst arrowtips", "Amethyst arrowtips", '2', 4),
    JAVELIN_HEADS("Amethyst javelin heads", "Amethyst javelin heads", '3', 12),
    DART_TIPS("Amethyst dart tips", "Amethyst dart tips", '4', 12);

    private final String label;
    private final String itemName;
    private final char menuEntry;
    private final int levelRequired;

    @Override
    public String toString()
    {
        return label;
    }

}