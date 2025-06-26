package org.ch2mpion.friendsystem.core;

import java.time.Instant;
import java.util.Objects; // Explicitly import Objects for hashCode
import java.util.UUID;

/**
 * Represents an in-memory friend request between two players.
 * Equality and hashing are based solely on the requester and requested player UUIDs,
 * meaning a unique request exists between a specific sender and receiver,
 * regardless of when it was sent.
 */
public class Request {

    private final UUID requesterId;
    private final UUID requestedId;
    private final Instant requestTime; // Timestamp when the request was made

    /**
     * Constructs a new friend request.
     * This constructor is package-private, intended to be called by PlayersManager.
     *
     * @param requesterId The UUID of the player who sent the request.
     * @param requestedId The UUID of the player who received the request.
     * @param requestTime The Instant when the request was created.
     */
    Request(UUID requesterId, UUID requestedId, Instant requestTime) {
        this.requesterId = requesterId;
        this.requestedId = requestedId;
        this.requestTime = requestTime;
    }

    /**
     * Gets the UUID of the player who sent this friend request.
     *
     * @return The requester's UUID.
     */
    public UUID getRequester() {
        return requesterId;
    }

    /**
     * Gets the UUID of the player who received this friend request.
     *
     * @return The requested player's UUID.
     */
    public UUID getRequestedId() {
        return requestedId;
    }

    /**
     * Gets the timestamp when this friend request was created.
     * This is used for determining request expiry.
     *
     * @return The Instant timestamp of the request.
     */
    public Instant getRequestTime() {
        return requestTime;
    }

    /**
     * Compares this Request object with another object for equality.
     * Two Request objects are considered equal if they have the same requesterId
     * and requestedId. The 'requestTime' is intentionally excluded from this comparison,
     * as the uniqueness of a request is defined by the sender-receiver pair.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        // Equality is based only on the unique pair of requester and requested UUIDs.
        return requesterId.equals(request.requesterId) &&
                requestedId.equals(request.requestedId);
    }

    /**
     * Generates a hash code for this Request object.
     * The hash code is generated based on the requesterId and requestedId,
     * consistent with the fields used in the {@link #equals(Object)} method.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        // Hashing based on requester and requested IDs to be consistent with equals().
        return Objects.hash(requesterId, requestedId);
    }
}