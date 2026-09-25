# Portable mirror of PRoot's src/loader/loader-info.awk for the MSI native
# build.  The upstream script uses gawk's strtonum() extension, which is not
# available in mawk/busybox awk (e.g. on F-Droid build machines); this version
# converts the hex symbol values with plain POSIX awk.
#
# Input:  `readelf -s <loader>` (the unstripped freestanding loader)
# Output: loader-info.c with the byte offset from _start to
#         pokedata_workaround, used by tracee/mem.c on arm64.
#
# readelf -s fields: Num:$1 Value:$2 Size:$3 Type:$4 Bind:$5 Vis:$6 Ndx:$7 Name:$8
$8 == "pokedata_workaround" { pokedata_workaround = hex2dec($2) }
$8 == "_start"              { start              = hex2dec($2) }
END {
	print "#include <unistd.h>"
	print "const ssize_t offset_to_pokedata_workaround=" (pokedata_workaround - start) ";"
}
function hex2dec(s,  i, c, v, n) {
	s = tolower(s)
	if (substr(s, 1, 2) == "0x") s = substr(s, 3)
	n = 0
	for (i = 1; i <= length(s); i++) {
		c = substr(s, i, 1)
		if (c >= "a" && c <= "f") v = index("abcdef", c) + 9
		else v = c + 0
		n = n * 16 + v
	}
	return n
}
