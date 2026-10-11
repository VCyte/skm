package dev.example.skmplugin;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import io.github.skm.api.ActionResult;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ActionManagerTest {
    @Test
    void restoresPreviousActionsWhenReplacementFails() {
        Plugin owner = fakePlugin();
        FakeRegistrationService service = new FakeRegistrationService();
        ActionExecutionService handler = (player, action, state) -> ActionResult.SUCCESS;
        ActionManager manager = new ActionManager(owner, service, handler);
        ActionDefinition previous = action("myskmplugin.previous");
        service.rejectedIds.add("myskmplugin.rejected");
        manager.replace(List.of(previous));

        assertThrows(IllegalArgumentException.class, () -> manager.replace(List.of(
                action("myskmplugin.new"), action("myskmplugin.rejected")
        )));

        assertEquals(List.of(previous), manager.actions());
        assertEquals(Set.of(previous.id()), service.ids());
    }

    @Test
    void duplicateIdDoesNotRemoveTheCurrentActionSet() {
        Plugin owner = fakePlugin();
        FakeRegistrationService service = new FakeRegistrationService();
        ActionManager manager = new ActionManager(owner, service,
                (player, action, state) -> ActionResult.SUCCESS);
        ActionDefinition previous = action("myskmplugin.previous");
        manager.replace(List.of(previous));

        assertThrows(IllegalArgumentException.class, () -> manager.replace(List.of(
                action("myskmplugin.duplicate"), action("myskmplugin.duplicate")
        )));

        assertEquals(List.of(previous), manager.actions());
        assertEquals(Set.of(previous.id()), service.ids());
    }

    @Test
    void closeUnregistersOnlyThisOwnerAndClearsLocalState() {
        Plugin owner = fakePlugin();
        Plugin otherOwner = fakePlugin();
        FakeRegistrationService service = new FakeRegistrationService();
        ActionManager manager = new ActionManager(owner, service,
                (player, action, state) -> ActionResult.SUCCESS);
        manager.replace(List.of(action("myskmplugin.owned")));
        service.register(otherOwner, action("otherplugin.owned"),
                (player, action, state) -> ActionResult.SUCCESS);

        manager.close();

        assertEquals(List.of(), manager.actions());
        assertEquals(Set.of("otherplugin.owned"), service.ids());
    }

    private static ActionDefinition action(String id) {
        return new ActionDefinition(id, id, "key.keyboard.g", 0, "test", false, null, 0);
    }

    private static Plugin fakePlugin() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "TestPlugin@" + System.identityHashCode(proxy);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                }
        );
    }

    private static final class FakeRegistrationService implements ActionRegistrationService {
        private final Map<String, OwnedAction> registered = new LinkedHashMap<>();
        private final Set<String> rejectedIds = new LinkedHashSet<>();

        @Override
        public void register(Plugin owner, ActionDefinition action, ActionExecutionService handler) {
            if (rejectedIds.contains(action.id())) {
                throw new IllegalArgumentException("simulated rejection for " + action.id());
            }
            if (registered.containsKey(action.id())) {
                throw new IllegalArgumentException("duplicate ID " + action.id());
            }
            registered.put(action.id(), new OwnedAction(owner, action, handler));
        }

        @Override
        public boolean unregister(Plugin owner, String actionId) {
            OwnedAction action = registered.get(actionId);
            if (action == null || action.owner() != owner) return false;
            registered.remove(actionId);
            return true;
        }

        @Override
        public int unregisterAll(Plugin owner) {
            int before = registered.size();
            registered.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
            return before - registered.size();
        }

        Set<String> ids() {
            return Set.copyOf(registered.keySet());
        }

        private record OwnedAction(Plugin owner, ActionDefinition definition, ActionExecutionService handler) {
        }
    }
}
