import { InferenceClient } from "@huggingface/inference";

export default async function handler(req,res){
  if(req.method!=="POST") return res.status(200).json({ok:true});
  const update=req.body||{};
  const message=update.message;
  if(!message) return res.status(200).json({ok:true});
  const text=message.text||"";
  if(!text.startsWith("/gerar")) return res.status(200).json({ok:true});
  const prompt=text.replace(/^\/gerar\s*/i,"").trim();
  if(!prompt) return res.status(200).json({ok:true});
  const client=new InferenceClient(process.env.HF_TOKEN);
  const image=await client.textToImage({model:process.env.IMAGE_MODEL||"black-forest-labs/FLUX.1-schnell",inputs:prompt});
  const bytes=Buffer.from(await image.arrayBuffer());
  const form=new FormData();
  form.append("chat_id",String(message.chat.id));
  form.append("photo",new Blob([bytes],{type:"image/png"}),"image.png");
  const r=await fetch("https://api.telegram.org/bot"+process.env.TELEGRAM_BOT_TOKEN+"/sendPhoto",{method:"POST",body:form});
  res.status(200).json(await r.json());
}