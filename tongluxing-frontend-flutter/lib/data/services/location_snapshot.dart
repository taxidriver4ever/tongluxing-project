class LocationSnapshot {
  const LocationSnapshot(this.latitude, this.longitude);

  final double latitude;
  final double longitude;

  static LocationSnapshot? current;

  // Used only before the first successful device/map location in the demo build.
  static const demoFallback = LocationSnapshot(23.5497, 116.3728);
}
