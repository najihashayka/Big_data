object Config {
  val uri = sys.env.getOrElse(
    "MONGODB_URI",
    throw new IllegalStateException("Set the MONGODB_URI environment variable before connecting to MongoDB.")
  )
}
