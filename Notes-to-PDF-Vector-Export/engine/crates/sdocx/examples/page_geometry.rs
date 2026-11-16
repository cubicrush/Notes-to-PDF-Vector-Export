fn main() -> Result<(), Box<dyn std::error::Error>> {
 let args: Vec<_>=std::env::args().collect();
 let doc=sdocx::parse(&args[1])?;
 let page: usize=args[2].parse()?;
 let strokes:Vec<_>=doc.pages[page].strokes().map(|s|sdocx::prepare_stroke(s,false)).collect();
 serde_json::to_writer(std::io::stdout().lock(),&strokes)?;
 Ok(())
}
