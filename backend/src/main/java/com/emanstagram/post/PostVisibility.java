package com.emanstagram.post;

/** Who may see a post. */
public enum PostVisibility {
    /** Anyone, including signed-out visitors of the login mosaic. */
    PUBLIC,
    /** The author's followers and the author. */
    FOLLOWERS,
    /** The author only. */
    PRIVATE
}
