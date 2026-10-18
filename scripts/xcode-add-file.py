# Adds a file in ios/App/App to the Xcode project without Xcode (there is no
# Mac here): to the App group, and to the Sources build phase (.swift) or the
# Resources phase (anything else).
#   python scripts/xcode-add-file.py ios/App/App.xcodeproj/project.pbxproj <filename> [<filetype>]
import hashlib, re, sys
pbx, name = sys.argv[1], sys.argv[2]
s = open(pbx, encoding='utf-8').read()
if f'/* {name} */ = {{isa = PBXFileReference' in s:
    print('already present'); sys.exit()
h = hashlib.md5(name.encode()).hexdigest().upper()
ref, build = h[:24], h[8:32]
swift = name.endswith('.swift')
ftype = sys.argv[3] if len(sys.argv) > 3 else ('sourcecode.swift' if swift else 'text')
phase = 'Sources' if swift else 'Resources'
s = s.replace('/* End PBXBuildFile section */',
  f'\t\t{build} /* {name} in {phase} */ = {{isa = PBXBuildFile; fileRef = {ref} /* {name} */; }};\n/* End PBXBuildFile section */', 1)
s = s.replace('/* End PBXFileReference section */',
  f'\t\t{ref} /* {name} */ = {{isa = PBXFileReference; lastKnownFileType = {ftype}; path = {name}; sourceTree = "<group>"; }};\n/* End PBXFileReference section */', 1)
s, n = re.subn(r'(/\* App \*/ = \{\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = \(\n)', lambda m: m.group(1) + f'\t\t\t\t{ref} /* {name} */,\n', s, count=1)
assert n == 1, 'App group not found'
s, n = re.subn(r'(/\* ' + phase + r' \*/ = \{\n\t\t\tisa = PBX' + phase + r'BuildPhase;\n\t\t\tbuildActionMask = \d+;\n\t\t\tfiles = \(\n)', lambda m: m.group(1) + f'\t\t\t\t{build} /* {name} in {phase} */,\n', s, count=1)
assert n == 1, phase + ' phase not found'
open(pbx, 'w', encoding='utf-8', newline='\n').write(s)
print('added', name)
