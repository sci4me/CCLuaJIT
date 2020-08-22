table.pack = function(...)
    return { n = select("#", ...), ... }
end
table.unpack = unpack