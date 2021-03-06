package biblemulticonverter.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import biblemulticonverter.data.Versification.Reference;

/**
 * A set of multiple {@link Versification}s and {@link VersificationMapping}s,
 * to be conveniently stored in a single file.
 */
public class VersificationSet {

	private static final String HEADER = "BibleMultiConverter-VersificationSet-1.0";

	private final List<Versification> versifications = new ArrayList<>();
	private final List<VersificationMapping> mappings = new ArrayList<>();

	public VersificationSet() {
	}

	public VersificationSet(File file) throws IOException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			loadFrom(br);
		}
	}

	public List<Versification> getVersifications() {
		return versifications;
	}

	public List<VersificationMapping> getMappings() {
		return mappings;
	}

	public Versification findVersification(String name) {
		for (Versification v : versifications) {
			if (v.getName().equals(name)) {
				return v;
			}
		}
		for (Versification v : versifications) {
			if (v.getAliases() != null && Arrays.asList(v.getAliases()).contains(name)) {
				return v;
			}
		}
		throw new NoSuchElementException("Versification " + name + " not found");
	}

	public VersificationMapping findMapping(String key) {
		String[] parts = key.split("/");
		if (parts.length == 2) {
			return findMapping(parts[0], parts[1], 0);
		} else if (parts.length == 3) {
			return findMapping(parts[0], parts[1], Integer.parseInt(parts[2]));
		} else {
			throw new IllegalArgumentException("Invalid mapping format: " + key);
		}
	}

	public VersificationMapping findMapping(String from, String to, int number) {
		Versification fromVersification = findVersification(from);
		Versification toVersification = findVersification(to);
		if (number == -1) {
			Map<Reference, List<Reference>> map = new HashMap<>();
			for (int i = 0; i < fromVersification.getVerseCount(); i++) {
				Reference r = fromVersification.getReference(i);
				if (toVersification.containsReference(r))
					map.put(r, Arrays.asList(r));
			}
			return VersificationMapping.build(fromVersification, toVersification, map);
		}
		List<VersificationMapping> candidates = new ArrayList<>();
		for (VersificationMapping mapping : mappings) {
			if (mapping.getFrom().getName().equals(from) && mapping.getTo().getName().equals(to))
				candidates.add(mapping);
		}
		if (candidates.isEmpty())
			throw new NoSuchElementException("No mapping found from " + from + " to " + to);
		if (number == 0 && mappings.size() == 1) {
			number = 1;
		} else if (number == 0) {
			Map<Reference, List<Reference>> map = new HashMap<>();
			for (int i = 0; i < fromVersification.getVerseCount(); i++) {
				Reference r = fromVersification.getReference(i);
				List<Reference> bestMapping = null;
				for (VersificationMapping candidate : candidates) {
					List<Reference> thisMapping = candidate.getMapping(r);
					if (thisMapping.isEmpty())
						thisMapping = null;
					if (bestMapping == null) {
						bestMapping = thisMapping;
					} else if (thisMapping != null) {
						bestMapping.retainAll(thisMapping);
						if (bestMapping.isEmpty())
							throw new NoSuchElementException("Unable to build best match from " + from + " to " + to + ": No common subset found for " + r);
					}
				}
				if (bestMapping != null) {
					map.put(r, bestMapping);
				}
			}
			return VersificationMapping.build(fromVersification, toVersification, map);
		} else if (number < 1 || number > candidates.size()) {
			throw new NoSuchElementException("Mapping " + number + " from " + from + " to " + to + " does not exist.");
		}
		return candidates.get(number - 1);
	}

	public void loadFrom(BufferedReader br) throws IOException {
		String header = br.readLine();
		if (!header.equals(HEADER))
			throw new IOException("Invalid file signature: " + header);
		header = br.readLine();
		while (header != null) {
			List<String> rules = new ArrayList<>();
			while (true) {
				String line = br.readLine();
				if (line != null && line.startsWith(" ")) {
					rules.add(line.substring(1));
				} else {
					if (header.contains(">") && !header.contains(" ")) {
						String[] parts = header.split(">", 2);
						Versification from = findVersification(parts[0]);
						Versification to = findVersification(parts[1]);
						mappings.add(VersificationMapping.fromRules(from, to, rules));
					} else {
						String[] parts = header.split(" ", 2);
						String name = parts[0], description = parts.length == 1 ? null : parts[1];
						versifications.add(Versification.fromVerseSets(name, description, rules));
					}
					header = line;
					break;
				}
			}
		}
	}

	public void saveTo(Writer w) throws IOException {
		w.write(HEADER + "\n");
		Set<String> versificationNames = new HashSet<>();
		for (Versification v : versifications) {
			if (!versificationNames.add(v.getName()))
				throw new IllegalStateException("Duplicate versification name: " + v.getName());
		}
		for (VersificationMapping m : mappings) {
			if (!versifications.contains(m.getFrom()))
				throw new IllegalStateException("Mapping references unknown versification " + m.getFrom().getName());
			if (!versifications.contains(m.getTo()))
				throw new IllegalStateException("Mapping references unknown versification" + m.getTo().getName());
		}
		for (Versification v : versifications) {
			w.write(v.getName());
			if (v.getDescription() != null)
				w.write(" " + v.getDescription());
			w.write('\n');
			if (v.getAliases() != null) {
				for (String alias : v.getAliases()) {
					w.write(" =" + alias + "\n");
				}
			}
			v.dumpVerseSets(w);
		}
		for (VersificationMapping m : mappings) {
			w.write(m.getFrom().getName() + ">" + m.getTo().getName() + '\n');
			m.dumpRules(w);
		}
	}
}
