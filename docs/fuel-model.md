# Gasoline fuel estimate

GreenWave uses a flat-road, warmed-up 2014 Mazda3 SKYACTIV-G 2.0 L reference model for both vehicles. The simulation integrates fuel every 0.1 s from actual vehicle speeds. The estimate omits engine gear selection, cold starts, slope, wind, climate control and start-stop. Results describe the simulation.

| Input | Value | Basis |
| --- | --- | --- |
| Equivalent test mass | 3,250 lb | EPA Mazda3 benchmark, Table 5 |
| Road load | 20.4 + 0.2116v + 0.01822v² lbf, with v in mph | EPA Mazda3 benchmark, Table 5 |
| Idle | 0.16 US gal/h, about 0.61 L/h | Argonne figure for a no-load 2.0 L gasoline compact car, reported by ORNL |
| Brake-specific fuel use | 650, 500, 335, 270, 245 g/kWh at 0, 5, 15, 30, 60 kW | Simplified power curve informed by the EPA Mazda map, not fitted measurement points |
| Drivetrain efficiency and gasoline density | 0.88 and 745 g/L | Model assumptions |
| Deceleration cutoff | 20 km/h with no positive traction demand | Model assumption for a warm engine in gear |

Road-load work is road force times distance. The change in kinetic energy adds to that work. Positive wheel work is converted to engine work and multiplied by interpolated fuel consumption. Idle flow is a floor when the engine is supplying power. With no positive traction work, fuel is zero only during deceleration above the cutoff, and idle flow otherwise. On a comparable route, stopping keeps the engine idling and can require the vehicle to regain kinetic energy. A longer detour or faster acceleration can offset that saving.

- [EPA Mazda SKYACTIV 2.0 L laboratory fuel map](https://www.epa.gov/sites/default/files/2016-11/documents/procs-gen-eng-fuel-cons-map-2014-mazda-2-0l-skyact.pdf)
- [EPA Mazda3 vehicle benchmark and road load, Table 5](https://www.epa.gov/sites/default/files/2016-10/documents/2016-01-1007-benchmark-hil-operat-2014-mazda-skyactiv-2.0l.pdf)
- [ORNL report with Argonne idle measurements](https://info.ornl.gov/sites/publications/Files/Pub67767.pdf)

For a predicted signal wait, GreenWave can release the throttle after reaching ordinary approach speed. Coast deceleration is road force divided by test mass plus a 0.15 m/s² engine-drag assumption, used when the configured braking ability permits that deceleration. The engine retains safety braking priority. Report zero injection only when the resulting tick has no positive tractive demand and stays above the cutoff.
