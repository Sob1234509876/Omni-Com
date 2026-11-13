package io.github.sob1234509876.oc.server.world;

/**
 * The inversion seam between the world and its live observers: one rarely changing fact of the world is
 * pushed to every connected client the moment it changes, instead of waiting for the next snapshot poll.
 *
 * <p>{@link WorldHost} lives below the transport layer and cannot know that browsers exist, so it does not
 * push anything itself: it owns the <em>trigger</em> (a dimension registered on a running world bumps
 * {@code contentVersion}) and hands the finished push frame to every broadcaster registered through
 * {@link WorldHost#addNotificationBroadcaster(NotificationBroadcaster)}. The game WebSocket handler
 * implements this interface and forwards the frame to its sessions; a future transport (or a diagnostic
 * recorder in a check) may implement it too. This follows the module's existing listener shape: the
 * {@link WorldTickHook} seam is the same inversion in the other direction, and the engine's event bus is
 * the same idea one layer lower.
 *
 * <p><strong>The contract is strict because of where it is called.</strong> The trigger fires on the world
 * thread while the simulation lock is held, so an implementation must be non-blocking and must not throw:
 *
 * <ul>
 *   <li>Never wait on I/O. Hand the frame to an executor or another buffer and return; the frame is a
 *       hint, so delivering it a few milliseconds late is always acceptable.</li>
 *   <li>Never let a failure escape. One dead or slow receiver may cost its own frame, never the world
 *       thread and never the frames of the other receivers.</li>
 *   <li>Never call back into the world. The host is mid-step; reading or writing world state here would
 *       deadlock or race.</li>
 * </ul>
 *
 * <p><strong>The frame is a hint, never state.</strong> Sessions that connect after a push miss it by
 * design and learn the fact from their next snapshot or bootstrap document; nothing is replayed. Correctness
 * never depends on the frame arriving: the snapshot's {@code contentVersion} handshake remains the fallback
 * that reconciles a client which lost or never received a push.
 */
@FunctionalInterface
public interface NotificationBroadcaster {

    /**
     * Delivers one server initiated push frame, in the OC/1 wire format, to this broadcaster's audience.
     *
     * <p>The frame is one line of text, for example
     * {@code NOTIFY {"type":"contentVersion","value":2}} - the {@code NOTIFY} prefix followed by one
     * compact JSON object whose {@code type} selects the meaning and whose other fields carry the
     * payload. See {@code protocol/oc-protocol.md} for the wire contract the client codes against.
     *
     * @param frame the finished push frame; never {@code null}
     */
    void broadcast(String frame);
}
